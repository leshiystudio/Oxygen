package sct.neuro;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import sct.IBot;
import sct.plants.Plant;
import sct.plants.PlantAction;
import sct.plants.PlantConfig;

//Тело растения (ростка) для нейросети (NeuroPlant): датчики — энергия, здоровье, почва, проверки
//check_list и соседи слева/впереди/справа; действия — PlantAction.
//Направление: 0 — лево, 1 — вперед, 2 — право (от головы). Параметры p0..p2 — типы потомков
//слева/впереди/справа для размножения: нет, росток, лист, проводник, корень.
public class PlantBody implements NeuroBody<Plant> {
	private static final String[] sides = {"left", "forward", "right"};
	private static final int[] birth_types = {Plant.EMPTY, Plant.ILIVE, Plant.LEAF, Plant.TRANS, Plant.ROOT};
	public static final PlantBody instance = new PlantBody();//после полей выше: конструктор их использует
	private final String[] inputs;
	private final String[] actions;

	private PlantBody() {
		List<String> in = new ArrayList<String>();
		for (String s : new String[]{"energy", "health", "age", "soil", "organics", "co2 around"}) {
			in.add(s);
		}
		for (int k = 0; k < 7; k++) {
			in.add("check " + k);
		}
		for (String side : sides) {
			for (int t = 0; t < Plant.type_names.length; t++) {
				in.add(side + "=" + Plant.type_names[t]);
			}
		}
		inputs = in.toArray(new String[0]);
		PlantAction[] all = PlantAction.values();
		actions = new String[all.length];
		for (int i = 0; i < all.length; i++) {
			actions[i] = all[i].id();
		}
	}
	public String[] inputs() {
		return inputs;
	}
	public void read(Plant p, float[] out) {
		PlantConfig c = p.w.c;
		int k = 0;
		out[k++] = (float) p.energy / Math.max(1, c.max_energy);
		out[k++] = (float) p.health / Math.max(1, c.max_health > 0 ? c.max_health : 1000);
		out[k++] = c.max_age > 0 ? (float) p.age / c.max_age : 0f;
		out[k++] = (float)(p.w.soil[p.xpos][p.ypos] / Math.max(1, c.max_energy_around));
		out[k++] = (float)(p.w.org[p.xpos][p.ypos] / 1000.0);
		out[k++] = (float)(p.co2_around() / (9 * sct.Constant.co2_render_maximum_coeff));
		for (int check = 0; check < 7; check++) {
			out[k++] = p.check(check) ? 1f : 0f;
		}
		int h = p.head % 4;
		int[] dirs = {(h + 3) % 4, h, (h + 1) % 4};
		for (int d : dirs) {
			int t = p.type_at(d);
			for (int i = 0; i < Plant.type_names.length; i++) {
				out[k++] = i == t ? 1f : 0f;
			}
		}
	}
	public String[] actions() {
		return actions;
	}
	public boolean enabled(Plant p, int action) {
		return PlantAction.values()[action].enabled_for(p);
	}
	public int dirs() {
		return 3;
	}
	public int params() {
		return 3;
	}
	public void act(Plant p, ListIterator<IBot> it, int action, int dir, float[] par) {
		PlantAction a = PlantAction.values()[action];
		switch (a) {
			case SHIFT_SOIL: a.run(p, it, dir); break;
			case MOVE: a.run(p, it, -1); break;
			case TURN: a.run(p, it, dir == 0 ? 0 : 2); break;
			case MUTATE: a.run(p, it, 1); break;
			case REPRODUCE: {
				int h = p.head % 4;
				int[] args = new int[7];
				int n = 0;
				for (int k = 0; k < 3; k++) {
					int t = birth_types[Math.min(birth_types.length - 1, (int)(par[k] * birth_types.length))];
					if (t != Plant.EMPTY) {
						args[1 + n * 2] = (h + 3 + k) % 4;
						args[2 + n * 2] = t;
						n++;
					}
				}
				if (n > 0) {
					args[0] = n;
					a.run(p, it, args);
				}
				break;
			}
			default: a.run(p, it);
		}
	}
}
