"""3D-визуализация мозга мухи в браузере: http://127.0.0.1:5580 (страница viz.html, three.js).

GET /            — страница
GET /neurons.bin — координаты нейронов: float32 x, y, z на нейрон (порядок индексов модели), центр 0, размах ~1
GET /meta        — JSON: классы нейронов (super_class) и группы привязки с индексами нейронов
GET /state?slot= — uint8 на нейрон: частота за последний ход / 2 (255 — 510 Гц и выше)
GET /info?slot=  — JSON: живые слоты, действие, частоты групп, номер хода
"""
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
VOXEL_NM = np.array([4.0, 4.0, 40.0])  # pos_x/y/z аннотаций FlyWire — в вокселях 4x4x40 нм


class Viz:
    def __init__(self, ids, ann, state, port=5580):
        """ids — root_id в порядке индексов модели; state — объект с полем ctl (server.State)."""
        self.state = state
        pos = ann.reindex(ids)[["pos_x", "pos_y", "pos_z"]].to_numpy(np.float64) * VOXEL_NM
        center = np.nanmean(pos, axis=0)
        pos = np.nan_to_num(pos - center)  # 14 нейронов без координат — в центр
        pos[:, 1] *= -1  # в FlyWire y растет вниз
        pos /= np.abs(pos).max()
        self.positions = pos.astype(np.float32).tobytes()
        sc = ann.reindex(ids)["super_class"].fillna("unknown").astype(str)
        self.classes = sorted(sc.unique())
        self.class_of = [self.classes.index(c) for c in sc]
        self.port = port

    def meta(self):
        ctl = self.state.ctl
        groups = {}
        if ctl is not None:
            for g in ctl.names:
                groups[g] = ctl.m.groups[g].cpu().tolist()
        return {"classes": self.classes, "class_of": self.class_of, "groups": groups,
                "inputs": sorted({g for _, gs, *_ in (ctl.m.sensors if ctl else []) for g in gs}),
                "version": id(ctl)}

    def info(self, slot):
        ctl = self.state.ctl
        if ctl is None:
            return {"ready": False}
        return {"ready": True, "version": id(ctl), "tick": ctl.ticks, "slots": sorted(ctl.last_act),
                "action": ctl.last_act.get(slot, "-"), "rates": ctl.group_rates(slot)}

    def activity(self, slot):
        ctl = self.state.ctl
        last = ctl.last_rate if ctl is not None else None  # пачка может смениться между строками — берем один тензор
        if last is None or slot >= last.shape[1]:
            return bytes(len(self.class_of))
        r = last[:, slot]
        return (r / 2).clamp(0, 255).to("cpu").numpy().astype(np.uint8).tobytes()

    def start(self):
        viz = self

        class H(BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def send(self, body, ctype):
                self.send_response(200)
                self.send_header("Content-Type", ctype)
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                u = urlparse(self.path)
                slot = int(parse_qs(u.query).get("slot", ["0"])[0])
                try:
                    if u.path == "/":
                        with open(os.path.join(HERE, "viz.html"), "rb") as f:
                            self.send(f.read(), "text/html; charset=utf-8")
                    elif u.path == "/neurons.bin":
                        self.send(viz.positions, "application/octet-stream")
                    elif u.path == "/meta":
                        self.send(json.dumps(viz.meta()).encode(), "application/json")
                    elif u.path == "/info":
                        self.send(json.dumps(viz.info(slot), ensure_ascii=False).encode(), "application/json")
                    elif u.path == "/state":
                        self.send(viz.activity(slot), "application/octet-stream")
                    else:
                        self.send_error(404)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        srv = ThreadingHTTPServer(("127.0.0.1", self.port), H)
        threading.Thread(target=srv.serve_forever, daemon=True, name="viz").start()
        print(f"визуализация мозга: http://127.0.0.1:{self.port}", flush=True)
