"""Привязка мозга мухи к телу бота (mapping.yaml): группы нейронов, датчики -> входы, выходы -> действия."""
import os

import pandas as pd
import torch
import yaml

from brain import DATA

SELECT_FIELDS = ("cell_type", "cell_class", "cell_sub_class", "super_class", "side")
DIR_NAMES = {"forward": 0, "right": 2, "back": 4, "left": 6}  # сдвиг от взгляда бота, как в BotBody


class MappingError(Exception):
    pass


def _as_list(x):
    return x if isinstance(x, list) else [x]


def _hint(name, allowed):
    near = [a for a in allowed if name.split("=")[0].strip() in a][:8]
    return f"{name!r} нет; есть: {', '.join(near or allowed[:40])}"


class Mapping:
    def __init__(self, path, brain, inputs, actions, annotations=None):
        """inputs, actions — имена датчиков и действий, которые прислал мир (BotBody)."""
        with open(path, encoding="utf-8") as f:
            m = yaml.safe_load(f)
        self.tick_ms = float(m.get("tick_ms", 10))
        self.smooth_ms = float(m.get("smooth_ms", 40))
        ann = annotations if annotations is not None else load_annotations()
        self.groups = {}  # имя -> индексы нейронов модели
        for name, sel in (m.get("groups") or {}).items():
            idx = self._select(ann, brain, name, sel or {})
            if len(idx) == 0:
                raise MappingError(f"группа {name}: селектор {sel} не нашел ни одного нейрона")
            self.groups[name] = idx
        self.sensors = []  # (индекс датчика, [группы], rate, invert)
        for s in m.get("sensors") or []:
            if s["input"] not in inputs:
                raise MappingError("датчик " + _hint(s["input"], inputs))
            to = [self._group(g, "датчик " + s["input"]) for g in _as_list(s["to"])]
            self.sensors.append((inputs.index(s["input"]), to, float(s.get("rate", 100)), bool(s.get("invert", False)),
                                 float(s.get("offset", 0))))
        self.actions = []  # dict: action (индекс), dir, params, from, gain, threshold
        for a in m.get("actions") or []:
            self.actions.append(self._action(a, actions, need_from=True))
        # default — действие или список: берется первое разрешенное мухе (у едока органики photo запрещено)
        self.defaults = [self._action(d, actions, need_from=False) for d in _as_list(m.get("default") or [])]

    def _group(self, name, where):
        if name not in self.groups:
            raise MappingError(f"{where}: нет группы {name!r}, есть: {', '.join(self.groups)}")
        return name

    def _action(self, a, actions, need_from):
        if a["action"] not in actions:
            raise MappingError("действие " + _hint(a["action"], actions))
        d = a.get("dir", "forward")
        if isinstance(d, str) and d not in DIR_NAMES:
            raise MappingError(f"действие {a['action']}: dir {d!r}, можно {', '.join(DIR_NAMES)} или 0..7")
        p = [float(x) for x in a.get("params", [0.5, 0.5])]
        r = {"action": actions.index(a["action"]), "name": a["action"], "dir": d,
             "params": (p + [0.5, 0.5])[:2], "gain": float(a.get("gain", 1)), "threshold": float(a.get("threshold", 0))}
        if need_from:
            r["from"] = self._group(a["from"], "действие " + a["action"])
        return r

    @staticmethod
    def _select(ann, brain, name, sel):
        unknown = set(sel) - set(SELECT_FIELDS) - {"ids"}
        if unknown:
            raise MappingError(f"группа {name}: поля {sorted(unknown)}, можно {', '.join(SELECT_FIELDS)}, ids")
        mask = pd.Series(True, index=ann.index)
        for f in SELECT_FIELDS:
            if f in sel:
                m = pd.Series(False, index=ann.index)
                for v in _as_list(sel[f]):
                    v = str(v)  # "PAM*" — все, что начинается с PAM
                    m |= ann[f].str.startswith(v[:-1]) if v.endswith("*") else ann[f] == v
                mask &= m
        if "ids" in sel:
            mask &= ann.index.isin([int(v) for v in _as_list(sel["ids"])])
        return brain.idx(ann.index[mask])


def load_annotations(data=DATA):
    a = pd.read_csv(os.path.join(data, "neuron_annotations.tsv"), sep="\t", low_memory=False,
                    usecols=["root_id", "pos_x", "pos_y", "pos_z", "soma_x", "soma_y", "soma_z", *SELECT_FIELDS])
    for f in SELECT_FIELDS:
        a[f] = a[f].astype(str)
    return a.set_index("root_id")


class Controller:
    """Мозг пачки мух + привязка: датчики слотов -> шаг мозга -> решение каждого слота."""

    MIN_BATCH = 8  # меньше не ужимаем: рост пачки — копирование всего состояния

    def __init__(self, brain, mapping, cap=None):
        """cap — сколько слотов может прислать мир; на видеокарте — пачка по самому большому живому слоту."""
        self.br = brain
        self.m = mapping
        self.cap = cap or brain.B
        self.rate = torch.zeros((len(mapping.groups), brain.B), device=brain.dev)  # сглаженные частоты групп
        self.names = list(mapping.groups)
        self.k = 1 - pow(2.718281828, -mapping.tick_ms / max(mapping.smooth_ms, 1e-6))
        # последний шаг — для визуализации (viz.py читает из своего потока)
        self.last_rate = torch.zeros((brain.N, brain.B), device=brain.dev)  # частоты нейронов за последний ход, Гц
        self.last_act = {}  # слот -> "действие dir"
        self.ticks = 0

    def _resize(self, B):
        if B == self.br.B:
            return
        k = min(B, self.br.B)
        self.br.resize(B)
        rate = torch.zeros((len(self.names), B), device=self.br.dev)
        rate[:, :k] = self.rate[:, :k]
        self.rate = rate
        self.last_rate = torch.zeros((self.br.N, B), device=self.br.dev)
        print(f"пачка мух на видеокарте: {B} (из {self.cap} слотов)", flush=True)

    def fit(self, used):
        """Пачка под слоты 0..used-1: растет вдвое, ужимается, когда живые занимают меньше четверти.
        Мир выдает мухам наименьшие свободные слоты, поэтому живые лежат плотно в начале."""
        B = self.br.B
        if used > B:
            self._resize(min(self.cap, max(used, 2 * B)))
        elif B > self.MIN_BATCH and used * 4 <= B:
            self._resize(max(self.MIN_BATCH, used * 2))

    def reset_slot(self, slot):
        if slot >= self.br.B:
            self.fit(slot + 1)
        b = self.br
        b.v[:, slot] = b.p["v_0"]
        for x in (b.g, b.rfc, b.counts, b.rate_in, b.poisson):
            x[:, slot] = 0
        b.inbuf[:, :, slot] = 0
        b.inputs_changed()
        self.rate[:, slot] = 0

    def step(self, flies):
        """flies: [(slot, values, enabled)] -> {slot: (имя действия, dir, params)}."""
        b, m = self.br, self.m
        if flies:
            self.fit(max(s for s, _, _ in flies) + 1)
        b.rate_in.zero_()
        b.poisson.zero_()
        for slot, values, _ in flies:
            for i, groups, rate, invert, offset in m.sensors:
                v = min(1.0, max(0.0, values[i]))
                v = 1 - v if invert else v
                v = max(0.0, (v - offset) / (1 - offset)) if offset < 1 else 0.0
                if v > 0:
                    for g in groups:
                        b.rate_in[m.groups[g], slot] += rate * v
        b.poisson.copy_(b.rate_in > 0)
        b.inputs_changed()
        r = b.run(m.tick_ms)
        now = torch.stack([r[m.groups[g]].mean(0) for g in self.names])
        self.rate += (now - self.rate) * self.k
        rate = self.rate.cpu().numpy()
        out = {}
        for slot, _, enabled in flies:
            best, score = None, -1.0
            for a in m.actions:
                s = a["gain"] * rate[self.names.index(a["from"]), slot]
                if s >= a["threshold"] and s > score and enabled[a["action"]]:
                    best, score = a, s
            if best is None:
                best = next((d for d in m.defaults if enabled[d["action"]]), None)
            out[slot] = best
        self.last_rate = r
        self.last_act = {s: (a["name"] + " " + str(a["dir"]) if a else "-") for s, a in out.items()}
        self.ticks += 1
        return out

    def group_rates(self, slot):
        if slot >= self.rate.shape[1]:
            return {g: 0.0 for g in self.names}
        return {g: float(self.rate[i, slot]) for i, g in enumerate(self.names)}
