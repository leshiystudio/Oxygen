package sct.plants;

import java.util.ListIterator;

import sct.Action;
import sct.IBot;

//Действия растения (ростка): todo_list biosim и размножение.
//Направления — относительно головы: 0 — лево, 1 — вперед, 2 — право (как в todo_list и think_new),
//у MOVE в THINK_OLD — абсолютное 0..3.
//Выключенное действие возвращает false — для мозга это «не удалось»: THINK_NEW выбирает
//следующий ген по ветке неудачи. Выключение — --set plant_actions_off=eat,move
public enum PlantAction implements Action<Plant> {
	SHIFT_SOIL("shift_soil", "energy", "side (0 лево, 1 вперед, 2 право)") {//перенести энергию почвы
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) { return p.shift_soil((p.head % 4 + 3 + a[0]) % 4); }
	},
	EAT("eat", "attack") {//съесть чужих вокруг
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) { return p.w.c.enable_eat && p.eat_all(); }
	},
	MOVE("move", "move", "dir (-1 — вперед, 0..3 — абсолютное)") {
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) {
			return p.can_move() && p.move(a.length == 0 || a[0] < 0 ? p.head % 4 : a[0]);
		}
	},
	TURN("turn", "move", "side (0 налево, 2 направо)") {
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) {
			p.head = (p.head % 4 + (a[0] == 0 ? 3 : 1)) % 4;
			return true;
		}
	},
	MUTATE("mutate", "genome", "genes") {//шанс мутации проверяет мозг
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) {
			p.mutate(a[0]);
			return true;
		}
	},
	REPRODUCE("reproduce", "birth", "n", "dir1", "type1", "dir2", "type2", "dir3", "type3") {//потомки: абсолютные направления 0..3 и типы
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) {
			int n = a[0];
			int[] dirs = new int[n];
			int[] types = new int[n];
			for (int k = 0; k < n; k++) {
				dirs[k] = a[1 + k * 2];
				types[k] = a[2 + k * 2];
			}
			return p.reproduce(dirs, types, n, it) > 0;
		}
	},
	ABSORB_CO2("absorb_co2", "energy") {//вытянуть CO2 из клеток 3x3 (газы Oxygen) и получить энергию
		protected boolean act(Plant p, ListIterator<IBot> it, int[] a) { return p.absorb_co2(); }
	};
	//
	private static final boolean[] on = new boolean[values().length];
	static {
		configure("");
	}
	private final String id;
	private final String category;
	private final String[] args;

	PlantAction(String id, String category, String... args) {
		this.id = id;
		this.category = category;
		this.args = args;
	}
	public String id() {
		return id;
	}
	public String category() {
		return category;
	}
	public String[] args() {
		return args;
	}
	public boolean enabled() {
		return on[ordinal()];
	}
	public boolean enabled_for(Plant body) {//галочка популяции растения (или общая, вне популяций)
		return body.w.pop != null ? body.w.pop.on(ordinal()) : on[ordinal()];
	}
	public boolean run(Plant body, ListIterator<IBot> iterator, int... args) {
		if (!enabled_for(body) || body.killed != 0) {
			return false;
		}
		return act(body, iterator, args);
	}
	protected abstract boolean act(Plant p, ListIterator<IBot> it, int[] a);
	//
	public static void configure(String off) {
		java.util.Arrays.fill(on, true);
		for (String name : off.split(",")) {
			name = name.trim();
			if (name.isEmpty()) {
				continue;
			}
			PlantAction found = null;
			for (PlantAction a : values()) {
				if (a.id.equals(name)) {
					found = a;
				}
			}
			if (found == null) {
				throw new IllegalArgumentException("нет действия растений " + name);
			}
			on[found.ordinal()] = false;
		}
	}
	public String toString() {
		return id;
	}
}
