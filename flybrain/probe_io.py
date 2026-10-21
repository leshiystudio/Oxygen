"""Таблица «вход -> выход»: каждая входная группа отдельно (своя муха в пачке) получает rate Гц,
печатаются частоты выходных групп. Помогает подобрать mapping.yaml.

python flybrain/probe_io.py [--rate 150] [--ms 300]
"""
import argparse

import pandas as pd
import torch

from brain import FlyBrain, load_connectome
from mapping import Mapping, load_annotations

INPUTS = {
    "looming_left": {"cell_type": ["LC4", "LPLC2"], "side": "left"},
    "looming_right": {"cell_type": ["LC4", "LPLC2"], "side": "right"},
    "sugar": {"cell_class": "gustatory", "cell_sub_class": "sugar"},
    "bitter": {"cell_class": "gustatory", "cell_sub_class": "bitter"},
    "water": {"cell_class": "gustatory", "cell_sub_class": "water"},
    "co2": {"cell_type": "ORN_V"},
    "food_odor": {"cell_type": "ORN_DM1"},
    "food_odor_left": {"cell_type": "ORN_DM1", "side": "left"},
    "food_odor_right": {"cell_type": "ORN_DM1", "side": "right"},
    "bad_odor": {"cell_type": "ORN_DA2"},
    "heating": {"cell_class": "thermosensory", "cell_sub_class": "heating"},
    "dry": {"cell_class": "hygrosensory", "cell_sub_class": "dry"},
    "light": {"cell_type": "R1-6"},
    "light_left": {"cell_type": "R1-6", "side": "left"},
    "light_right": {"cell_type": "R1-6", "side": "right"},
    "wind": {"cell_class": "mechanosensory", "cell_sub_class": "wind_gravity"},
    "touch": {"cell_class": "mechanosensory", "cell_sub_class": "head bristle"},
    "touch_left": {"cell_class": "mechanosensory", "cell_sub_class": "head bristle", "side": "left"},
    "touch_right": {"cell_class": "mechanosensory", "cell_sub_class": "head bristle", "side": "right"},
}
OUTPUTS = {
    "walk": {"cell_type": "DNp09"},
    "walk_back": {"cell_type": "MDN"},
    "steer_L": {"cell_type": ["DNa01", "DNa02"], "side": "left"},
    "steer_R": {"cell_type": ["DNa01", "DNa02"], "side": "right"},
    "escape": {"cell_type": "DNp01"},
    "proboscis": {"cell_type": "CB0701"},
    "groom": {"cell_type": "DNg11"},
    "wings": {"cell_type": ["DNp02", "DNp04", "DNp11"]},
    "egg": {"cell_type": "oviDN*"},
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rate", type=float, default=150)
    ap.add_argument("--ms", type=float, default=300)
    a = ap.parse_args()
    con = load_connectome()
    ann = load_annotations()
    br = FlyBrain(batch=len(INPUTS), mode="event", params={"dt": 0.5}, connectome=con)
    ins = {k: Mapping._select(ann, br, k, s) for k, s in INPUTS.items()}
    outs = {k: Mapping._select(ann, br, k, s) for k, s in OUTPUTS.items()}
    for slot, (k, idx) in enumerate(ins.items()):
        br.set_input(idx, a.rate, fly=slot)
    r = br.run(a.ms)
    table = pd.DataFrame({o: [float(r[idx, s].mean()) for s in range(len(ins))] for o, idx in outs.items()},
                         index=[f"{k} ({len(i)})" for k, i in ins.items()])
    pd.set_option("display.width", 200)
    print(f"частоты выходных групп, Гц ({a.rate:g} Гц на вход, {a.ms:g} мс):")
    print(table.round(0).astype(int).to_string())


if __name__ == "__main__":
    with torch.no_grad():
        main()
