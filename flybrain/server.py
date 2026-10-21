"""Сервис мозга мухи для Oxygen: мир на Java подключается по TCP (localhost) и раз в ход
присылает датчики всех мух одной пачкой. Обычно его запускает сам мир (sct.neuro.FlyBrain);
вручную: python flybrain/server.py [--port 5577]

Протокол — строки UTF-8:
  мир:    hello <слотов>\t<датчики через |>\t<действия через |>
  сервис: ok <рекомендуемо слотов>\t<сообщение>   или   error <текст>
  мир:    step <слот>:<включенные действия 0/1>:<датчик,датчик,...>;<слот>:...
  сервис: acts <слот>:<действие>:<dir>:<p0>,<p1>;...   (действие "-" — ничего не делать)
  мир:    reset <слот> | rates <слот>   сервис: ok | rates <группа>=<Гц>,...
"""
import argparse
import ipaddress
import os
import socketserver
import sys
import time
import traceback

import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brain import FlyBrain, load_connectome  # noqa: E402
from mapping import Controller, Mapping, MappingError, load_annotations  # noqa: E402
from viz import Viz  # noqa: E402

TARGET_TICKS = 20  # рекомендуемое число слотов — сколько мух дают миру не меньше стольких ходов в секунду


def make_brain(con, slots, dt, batch=None):
    # одна муха — плотная матрица и CUDA graph, пачка — событийный режим (см. замеры в validate.py).
    # batch — сколько мух держать на видеокарте сразу (дальше пачка растет по живым мухам, Controller.fit)
    return FlyBrain(batch=batch or slots, mode="dense" if slots == 1 else "event", params={"dt": dt}, connectome=con)


def recommend(con, tick_ms, dt, limit=128):
    """Замер: сколько слотов дают TARGET_TICKS ходов в секунду при tick_ms на ход."""
    best = 1
    b = 1
    while b <= limit:
        br = make_brain(con, b, dt)
        br.set_input(torch.arange(0, 2000, 7, device=br.dev), 100.0)  # фон, похожий на работу
        br.run(tick_ms)
        torch.cuda.synchronize()
        t = time.time()
        for _ in range(5):
            br.run(tick_ms)
        torch.cuda.synchronize()
        tps = 5 / (time.time() - t)
        print(f"  слотов {b:3d}: {tps:6.1f} ходов/с", flush=True)
        del br
        torch.cuda.empty_cache()
        if tps < TARGET_TICKS:
            break
        best = b
        b *= 2
    return best


class State:
    con = ann = None
    ctl = None
    args = None
    allow = []


class Handler(socketserver.StreamRequestHandler):
    def send(self, s):
        self.wfile.write((s + "\n").encode("utf-8"))
        self.wfile.flush()

    def handle(self):
        addr = ipaddress.ip_address(self.client_address[0])
        if not any(addr in net for net in State.allow):
            print(f"отказ: {addr} не входит в --allow", flush=True)
            return
        print(f"мир подключился ({addr})", flush=True)
        for raw in self.rfile:
            line = raw.decode("utf-8").rstrip("\r\n")
            try:
                self.send(self.command(line))
            except MappingError as e:
                self.send("error " + str(e).replace("\n", " "))
            except Exception as e:
                traceback.print_exc()
                self.send("error " + repr(e).replace("\n", " "))
        print("мир отключился", flush=True)

    def command(self, line):
        op, _, rest = line.partition(" ")
        if op == "hello":
            slots, inputs, actions = rest.split("\t")
            slots = max(1, int(slots))
            a = State.args
            State.ctl = None
            brain = make_brain(State.con, slots, a.dt, batch=min(slots, Controller.MIN_BATCH))
            m = Mapping(a.mapping, brain, inputs.split("|"), actions.split("|"), State.ann)
            rec = recommend(State.con, m.tick_ms, a.dt) if a.bench else 0
            State.ctl = Controller(brain, m, cap=slots)
            sizes = ", ".join(f"{g} {len(i)}" for g, i in State.ctl.m.groups.items())
            msg = f"мух {slots}, {m.tick_ms:g} мс мозга на ход; группы: {sizes}"
            print(msg, flush=True)
            return f"ok {rec}\t{msg}"
        if State.ctl is None:
            return "error сначала hello"
        if op == "step":
            flies = []
            for part in filter(None, rest.split(";")):
                slot, mask, vals = part.split(":")
                flies.append((int(slot), [float(x) for x in vals.split(",")] if vals else [], [c == "1" for c in mask]))
            acts = State.ctl.step(flies)
            out = []
            for slot, a in acts.items():
                if a is None:
                    out.append(f"{slot}:-:0:0,0")
                else:
                    out.append(f"{slot}:{a['name']}:{a['dir']}:{a['params'][0]:g},{a['params'][1]:g}")
            return "acts " + ";".join(out)
        if op == "reset":
            State.ctl.reset_slot(int(rest))
            return "ok"
        if op == "rates":
            return "rates " + ",".join(f"{g}={r:.1f}" for g, r in State.ctl.group_rates(int(rest)).items())
        return "error неизвестная команда " + op


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5577)
    ap.add_argument("--host", default="127.0.0.1", help="0.0.0.0 — принимать мир из Docker/WSL (см. --allow)")
    ap.add_argument("--allow", default="127.0.0.0/8,172.16.0.0/12",
                    help="с каких адресов пускать мир, через запятую (по умолчанию — эта машина и подсети WSL/Docker)")
    ap.add_argument("--mapping", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "mapping.yaml"))
    ap.add_argument("--dt", type=float, default=0.5)
    ap.add_argument("--viz-port", type=int, default=5580, help="3D-визуализация в браузере (0 — выключить)")
    ap.add_argument("--no-bench", dest="bench", action="store_false", help="не замерять рекомендуемое число мух")
    State.args = ap.parse_args()
    State.allow = [ipaddress.ip_network(n.strip()) for n in State.args.allow.split(",") if n.strip()]
    if not torch.cuda.is_available():
        print("нет CUDA: мозг мухи считается только на видеокарте NVIDIA", flush=True)
        sys.exit(1)
    t = time.time()
    State.con = load_connectome()
    State.ann = load_annotations()
    print(f"мозг мухи: {len(State.con[0])} нейронов, {len(State.con[1])} связей ({time.time() - t:.1f} с), "
          f"{State.args.host}:{State.args.port}, пускаю {State.args.allow}", flush=True)
    if State.args.viz_port > 0:
        Viz(State.con[0], State.ann, State, State.args.viz_port).start()
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer((State.args.host, State.args.port), Handler) as srv:
        srv.serve_forever()


if __name__ == "__main__":
    main()
