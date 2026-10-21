"""LIF-модель всего мозга мухи на PyTorch (GPU) по Shiu et al., Nature 2024.

Связи — FlyWire v783 (Connectivity_783.parquet из philshiu/Drosophila_brain_model):
вес = число синапсов x знак медиатора x w_syn. Нейрон:
    dv/dt = (v_0 - v + g) / t_mbr,   dg/dt = -g / tau
импульс при v > v_th, затем v = v_rst, g = 0 и рефрактерность t_rfc; импульс приходит
к постсинаптическим нейронам через t_dly и добавляет вес к g.

Пачка из B мух: матрица связей общая, состояние (N, B) — свое у каждой.
"""
import math
import os

import numpy as np
import pandas as pd
import torch

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

PARAMS = {  # как default_params в model.py Shiu, единицы — мВ и мс
    "v_0": -52.0,
    "v_rst": -52.0,
    "v_th": -45.0,
    "t_mbr": 20.0,
    "tau": 5.0,
    "t_rfc": 2.2,
    "t_dly": 1.8,
    "w_syn": 0.275,
    "f_poi": 250.0,  # вес пуассоновского входа = w_syn * f_poi
    "dt": 0.1,
}


def load_connectome(data=DATA):
    """Нейроны (root_id в порядке индексов модели) и связи pre -> post с весом в синапсах со знаком."""
    ids = pd.read_csv(os.path.join(data, "Completeness_783.csv"), index_col=0).index.to_numpy()
    con = pd.read_parquet(os.path.join(data, "Connectivity_783.parquet"),
                          columns=["Presynaptic_Index", "Postsynaptic_Index", "Excitatory x Connectivity"])
    pre = con["Presynaptic_Index"].to_numpy(np.int64)
    post = con["Postsynaptic_Index"].to_numpy(np.int64)
    w = con["Excitatory x Connectivity"].to_numpy(np.float32)
    return ids, pre, post, w


class FlyBrain:
    def __init__(self, batch=1, device="cuda", params=None, mode="event", connectome=None):
        p = dict(PARAMS)
        p.update(params or {})
        self.p = p
        self.dev = torch.device(device)
        self.B = batch
        self.mode = mode  # event — только по импульсам; dense — умножение всей матрицы каждый шаг
        ids, pre, post, w = connectome if connectome is not None else load_connectome()
        self.ids = ids
        self.index = {int(r): i for i, r in enumerate(ids)}
        self.N = N = len(ids)
        w = torch.from_numpy(w * p["w_syn"])
        # по пресинаптическому нейрону (CSR строки = pre): исходящие связи спайкнувших
        order = np.argsort(pre, kind="stable")
        self.out_ptr = torch.from_numpy(np.concatenate([[0], np.cumsum(np.bincount(pre, minlength=N))])).to(self.dev)
        self.out_post = torch.from_numpy(post[order]).to(self.dev)
        self.out_w = w[order].to(self.dev)
        if mode == "dense":
            ii = torch.from_numpy(np.stack([post, pre]))
            self.W = torch.sparse_coo_tensor(ii, w, (N, N)).coalesce().to_sparse_csr().to(self.dev)
        dt = p["dt"]
        self.D = max(1, round(p["t_dly"] / dt))  # задержка в шагах
        self.R = round(p["t_rfc"] / dt)
        self.eg = math.exp(-dt / p["tau"])
        self.ev = math.exp(-dt / p["t_mbr"])
        self.kgv = (self.eg - self.ev) / (1 - p["t_mbr"] / p["tau"])  # вклад g в v за шаг (точное решение)
        self.reset()

    def reset(self):
        if getattr(self, "graph", None) is not None:  # граф держит адреса тензоров — обнуляем на месте
            self.v.fill_(self.p["v_0"])
            for x in (self.g, self.rfc, self.inbuf, self.rate_in, self.poisson, self.counts):
                x.zero_()
            self.t = 0
            return
        N, B, d = self.N, self.B, self.dev
        self.v = torch.full((N, B), self.p["v_0"], device=d)
        self.g = torch.zeros((N, B), device=d)
        self.rfc = torch.zeros((N, B), dtype=torch.int16, device=d)
        self.inbuf = torch.zeros((self.D, N, B), device=d)  # входы, которые придут через k шагов
        self.t = 0
        self.rate_in = torch.zeros((N, B), device=d)  # частота пуассоновского входа, Гц
        self.poisson = torch.zeros((N, B), dtype=torch.bool, device=d)
        self.counts = torch.zeros((N, B), dtype=torch.int32, device=d)
        self.inputs_changed()

    def resize(self, B):
        """Сменить число мух в пачке: состояние первых min(B, старое) мух сохраняется, новые — в покое."""
        if B == self.B:
            return
        k = min(B, self.B)

        def grown(old, fill=0):
            new = torch.full(old.shape[:-1] + (B,), fill, dtype=old.dtype, device=old.device)
            new[..., :k] = old[..., :k]
            return new

        self.v = grown(self.v, self.p["v_0"])
        self.g, self.rfc, self.inbuf = grown(self.g), grown(self.rfc), grown(self.inbuf)
        self.rate_in, self.poisson, self.counts = grown(self.rate_in), grown(self.poisson), grown(self.counts)
        self.B = B
        self.graph = None
        self.inputs_changed()

    def idx(self, root_ids):
        return torch.tensor([self.index[int(r)] for r in root_ids if int(r) in self.index],
                            dtype=torch.long, device=self.dev)

    def set_input(self, neurons, rate_hz, fly=None):
        """Пуассоновский вход rate_hz на нейроны (индексы модели); fly=None — всем мухам."""
        cols = slice(None) if fly is None else fly
        self.rate_in[neurons, cols] = rate_hz
        self.poisson[neurons, cols] = rate_hz > 0
        self.inputs_changed()

    def inputs_changed(self):
        """rate_in / poisson изменены напрямую — пересчитать список входов перед следующим прогоном."""
        self._in_idx = None

    def _inputs(self):
        # событийный режим: случайный вход разыгрывается только для пар «нейрон, муха» с входом,
        # а не для всех N x B (у мухи входов — сотни из 139 тыс.)
        if self._in_idx is None:
            flat = self.poisson.view(-1).nonzero().squeeze(1)
            self._in_idx = flat
            self._in_prob = self.rate_in.view(-1)[flat] * (self.p["dt"] / 1000.0)
        return self._in_idx, self._in_prob

    def _propagate(self, spk):
        slot = self.t % self.D  # этот слот уже прочитан: сюда пишем то, что придет через D шагов
        if self.mode == "dense":
            self.inbuf[slot] += torch.sparse.mm(self.W, spk.float())
            return
        n, b = spk.nonzero(as_tuple=True)
        if n.numel() == 0:
            return
        start = self.out_ptr[n]
        cnt = self.out_ptr[n + 1] - start
        tot = int(cnt.sum())
        if tot == 0:
            return
        first = torch.cumsum(cnt, 0) - cnt
        eid = torch.repeat_interleave(start - first, cnt) + torch.arange(tot, device=self.dev)
        tgt = self.out_post[eid] * self.B + torch.repeat_interleave(b, cnt)
        self.inbuf[slot].view(-1).index_add_(0, tgt, self.out_w[eid])

    def step(self):
        # все обновления на месте: тензоры состояния не меняют адресов, иначе не работает CUDA graph
        p = self.p
        slot = self.t % self.D
        arrived = self.inbuf[slot]
        self.g += arrived
        arrived.zero_()
        active = self.rfc <= 0
        v_new = p["v_0"] + (self.v - p["v_0"]) * self.ev + self.g * self.kgv
        self.v.copy_(torch.where(active, v_new, self.v))
        self.g.copy_(torch.where(active, self.g * self.eg, self.g))
        if p.get("poisson", True):
            if self.mode == "dense":  # одна муха в CUDA graph: размеры тензоров постоянны, разыгрываем все
                kick = self.poisson & (torch.rand_like(self.v) < self.rate_in * (p["dt"] / 1000.0))
                self.v += kick * (p["w_syn"] * p["f_poi"])
            else:
                idx, prob = self._inputs()
                if idx.numel():
                    hit = (torch.rand(idx.numel(), device=self.dev) < prob).float()
                    self.v.view(-1).index_add_(0, idx, hit * (p["w_syn"] * p["f_poi"]))
            active = active | self.poisson  # у нейронов с внешним входом нет рефрактерности (как у Shiu)
        spk = active & (self.v > p["v_th"])
        self.v.masked_fill_(spk, p["v_rst"])
        self.g.masked_fill_(spk, 0.0)
        self.rfc.copy_(torch.where(spk, torch.full_like(self.rfc, self.R), (self.rfc - 1).clamp_min(0)))
        self.counts += spk
        self._propagate(spk)
        self.t += 1
        return spk

    def _capture(self):
        """Записать D шагов (полный оборот буфера задержки) в CUDA graph — только для dense."""
        s = torch.cuda.Stream()
        s.wait_stream(torch.cuda.current_stream())
        with torch.cuda.stream(s):  # прогрев перед записью, как требует PyTorch
            for _ in range(self.D):
                self.step()
        torch.cuda.current_stream().wait_stream(s)
        self.graph = torch.cuda.CUDAGraph()
        with torch.cuda.graph(self.graph):
            for _ in range(self.D):
                self.step()

    def run(self, ms):
        """Прогнать ms миллисекунд; вернуть частоты (Гц) за этот отрезок, (N, B)."""
        steps = round(ms / self.p["dt"])
        self.counts.zero_()
        if self.mode == "dense" and self.dev.type == "cuda":
            while self.t % self.D and steps > 0:  # граф начинается со слота 0
                self.step()
                steps -= 1
            if getattr(self, "graph", None) is None:
                self._capture()  # прогрев внутри сдвигает состояние на D шагов — не страшно
            for _ in range(steps // self.D):
                self.graph.replay()
                self.t += self.D
            for _ in range(steps % self.D):
                self.step()
        else:
            for _ in range(steps):
                self.step()
        return self.counts.float() * (1000.0 / ms)
