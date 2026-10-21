package sct.plants;

import java.util.ListIterator;

import sct.Brain;
import sct.IBot;

//Мозг THINK_OLD из biosim: линейный геном с указателем команды.
//Команды 0..2 — размножение, 0..3 — движение, 4..7 — еда, 23/24 — условные переходы,
//25 — мутация, больше 25 — прыжок указателя. Больше max_step переходов за ход — штраф.
public class ThinkOld implements Brain<Plant> {
	public static final ThinkOld instance = new ThinkOld();

	public String name() {
		return "think_old";
	}
	static int old_type(int v) {
		int m = v % 4;
		if (m == 0) {
			return Plant.ROOT;
		}
		if (m == 1) {
			return Plant.TRANS;
		}
		if (m == 2) {
			return Plant.LEAF;
		}
		return Plant.ILIVE;
	}
	public void think(Plant p, ListIterator<IBot> it) {
		PlantConfig c = p.w.c;
		int gs = c.genome_size();
		int override = -1;
		int steps = 0;
		while (true) {
			steps++;
			if (steps > c.max_step) {
				p.take_energy(c.punish_over_max_step, Plant.PUNISH);
				break;
			}
			if (p.killed != 0 || !Plant.is_alive_type(p.type)) {
				break;
			}
			int ci = p.cmd;
			int cmd;
			if (override >= 0) {
				cmd = override;
				override = -1;
			}else {
				cmd = p.genome[ci] & 0xFF;
			}
			int nx1 = p.genome[(ci + 1) % gs] & 0xFF;
			int nx2 = p.genome[(ci + 2) % gs] & 0xFF;
			int nx3 = p.genome[(ci + 3) % gs] & 0xFF;

			if (cmd < 3 && p.type == Plant.ILIVE) {
				int n = nx1 % 4;
				if (n > 0) {
					int[] a = new int[1 + n * 2];
					a[0] = n;
					for (int k = 0; k < n; k++) {
						a[1 + k * 2] = (cmd + k) % 4;
						a[2 + k * 2] = old_type(nx2);
					}
					PlantAction.REPRODUCE.run(p, it, a);
				}
			}
			if (cmd < 4) {
				PlantAction.MOVE.run(p, it, cmd);
			}
			if (4 <= cmd && cmd < 8) {
				PlantAction.EAT.run(p, it);
			}
			if (cmd == 23) {//переход: мало энергии -> nx1, иначе -> nx2
				override = p.energy < c.cost_birth ? nx1 : nx2;
				p.cmd = (ci + 1) % gs;
				continue;
			}
			if (cmd == 24) {//переход: энергии больше nx1 -> nx2, иначе -> nx3
				override = p.energy > nx1 ? nx2 : nx3;
				p.cmd = (ci + 1) % gs;
				continue;
			}
			if (cmd == 25 && PlantWorld.rand.nextDouble() < c.percent_mutation_am) {
				int cnt = nx1 % 2 == 0 ? 1 : 1 + nx2 % c.max_mutation_genes;
				PlantAction.MUTATE.run(p, it, cnt);
			}
			if (cmd > 25) {
				p.cmd = (p.cmd + cmd) % gs;
			}
			break;
		}
		if (p.killed == 0 && Plant.is_alive_type(p.type)) {
			p.cmd = (p.cmd + 1) % gs;
		}
	}
}
