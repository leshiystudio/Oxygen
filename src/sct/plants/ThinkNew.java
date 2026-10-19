package sct.plants;

import java.util.ListIterator;

import sct.Brain;
import sct.IBot;

//Мозг THINK_NEW из biosim: активный ген из genome_cols команд.
//Команды 3 и 5 ниже think_threshold — две проверки и действие с переходом на другой ген,
//иначе команды 0..2 — типы потомков слева, впереди и справа.
public class ThinkNew implements Brain<Plant> {
	public static final ThinkNew instance = new ThinkNew();

	public String name() {
		return "think_new";
	}
	public void think(Plant p, ListIterator<IBot> it) {
		PlantConfig c = p.w.c;
		int c3 = p.gcmd(3);
		int c5 = p.gcmd(5);
		if (c3 < c.think_threshold && c5 < c.think_threshold) {
			boolean r3 = p.check_list(c3 % c.check_mod);
			boolean r5 = p.check_list(c5 % c.check_mod);
			if (r3 && r5) {
				boolean ok = todo(p, it, p.gcmd(9) % c.todo_mod);
				p.gen = ok ? p.gcmd(10) : p.gcmd(11);
			}else {
				int c12 = p.gcmd(12);
				if (c12 > 2) {
					p.gen = p.gcmd(8);
				}
				boolean ok = todo(p, it, c12 % c.todo_mod);
				p.gen = ok ? p.gcmd(13) : p.gcmd(14);
			}
			return;
		}
		//размножение: команды 0, 1, 2 активного гена — типы потомков слева, впереди, справа
		int h = p.head % 4;
		int[] a = new int[7];
		int n = 0;
		for (int k = 0; k < 3; k++) {
			int t = p.type_from_cmd(p.gcmd(k));
			if (t != Plant.EMPTY) {
				a[1 + n * 2] = (h + 3 + k) % 4;//k=0 — лево, 1 — вперед, 2 — право
				a[2 + n * 2] = t;
				n++;
			}
		}
		if (n > 0) {
			a[0] = n;
			PlantAction.REPRODUCE.run(p, it, a);
		}
	}
	//todo_list: действие ростка; true — удалось
	static boolean todo(Plant p, ListIterator<IBot> it, int k) {
		switch (k) {
			case 0: return PlantAction.SHIFT_SOIL.run(p, it, 0);
			case 1: return PlantAction.SHIFT_SOIL.run(p, it, 1);
			case 2: return PlantAction.SHIFT_SOIL.run(p, it, 2);
			case 3: return PlantAction.EAT.run(p, it);
			case 4: return PlantAction.MOVE.run(p, it, -1);
			case 5: return PlantAction.TURN.run(p, it, 0);
			case 6: return PlantAction.TURN.run(p, it, 2);
			case 7: return PlantWorld.rand.nextDouble() < p.w.c.percent_mutation_am && PlantAction.MUTATE.run(p, it, 1);
			case 8: return PlantAction.ABSORB_CO2.run(p, it);//только при todo_mod >= 9
			default: return false;
		}
	}
}
