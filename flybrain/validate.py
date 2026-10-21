"""Проверка модели по Shiu 2024: сладкие рецепторы -> MN9 (моторный нейрон хоботка) и замер скорости.

python flybrain/validate.py [--mode event|dense] [--ms 1000] [--batch 1,8,32]
"""
import argparse
import time

import torch

from brain import FlyBrain, load_connectome

# сладкие рецепторы из example.ipynb Shiu (v630; 20 из 21 есть в v783) и MN9 (CB0701)
SUGAR = [720575940624963786, 720575940630233916, 720575940637568838, 720575940638202345,
         720575940617000768, 720575940630797113, 720575940632889389, 720575940621754367,
         720575940621502051, 720575940640649691, 720575940639332736, 720575940616885538,
         720575940639198653, 720575940620900446, 720575940617937543, 720575940632425919,
         720575940633143833, 720575940612670570, 720575940628853239, 720575940629176663,
         720575940611875570]
MN9 = 720575940660219265


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", default="event")
    ap.add_argument("--ms", type=float, default=1000)
    ap.add_argument("--batch", default="1")
    ap.add_argument("--hz", type=float, default=100)
    a = ap.parse_args()
    t0 = time.time()
    con = load_connectome()
    print(f"connectome: {len(con[0])} нейронов, {len(con[1])} связей, загрузка {time.time() - t0:.1f} с")
    for B in [int(x) for x in a.batch.split(",")]:
        br = FlyBrain(batch=B, mode=a.mode, connectome=con)
        sugar = br.idx(SUGAR)
        mn9 = br.index[MN9]
        # половина мух — сахар, половина — контроль без входа (у B=1 только сахар)
        fly = slice(0, max(1, B // 2))
        br.set_input(sugar, a.hz, fly=fly)
        br.run(20)  # прогрев CUDA
        br.reset()
        br.set_input(sugar, a.hz, fly=fly)
        torch.cuda.synchronize()
        t0 = time.time()
        rate = br.run(a.ms)
        torch.cuda.synchronize()
        wall = time.time() - t0
        r = rate[:, 0]
        active = int((r > 0).sum())
        print(f"B={B:3d} mode={a.mode}: {a.ms:.0f} мс мозга за {wall:.2f} с "
              f"({a.ms / 1000 / wall:.3f} x реального времени на муху, {B * a.ms / 1000 / wall:.2f} x на пачку)")
        print(f"   сахар {a.hz:.0f} Гц: MN9 = {r[mn9]:.1f} Гц, активных нейронов {active}, "
              f"сахарные рецепторы {r[sugar].mean():.0f} Гц")
        if B > 1:
            print(f"   контроль (без входа): MN9 = {rate[mn9, -1]:.1f} Гц, активных {int((rate[:, -1] > 0).sum())}")
        print(f"   GPU память: {torch.cuda.max_memory_allocated() / 2**20:.0f} МБ")
        del br
        torch.cuda.empty_cache()
        torch.cuda.reset_peak_memory_stats()


if __name__ == "__main__":
    main()
