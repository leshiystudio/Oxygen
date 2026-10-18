package sct;

import java.util.ListIterator;

//Действия тела бота Oxygen (и нейро-бота — у него то же тело).
//Код каждого действия — метод Bot, перенесенный из update_commands без изменений;
//здесь только имя, категория, параметры и включение.
//
//Направление — 0..7 (как в movelist) или FORWARD — куда смотрит бот.
//Выключенное действие ничего не делает: геном при этом сдвигает указатель как обычно.
//Выключение — Constant.bot_actions_off (через запятую), например --set bot_actions_off=attack,seed
public enum BotAction implements Action<Bot> {
	TURN("turn", "move", "angle") {//повернуть на угол
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.turn(a[0]); }
	},
	SET_DIR("set_dir", "move", "dir") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.set_dir(a[0]); }
	},
	RANDOM_DIR("random_dir", "move") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.random_dir(); }
	},
	MOVE("move", "move", "dir") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.move_cmd(b.dir(a[0])); }
	},
	PHOTO("photo", "energy") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.photosynthesis(); }
	},
	RECYCLE("recycle", "energy", "dir (-1 — под собой)", "amount") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.recycle_cmd(a[0] < 0 ? -1 : b.dir(a[0]), a[1]); }
	},
	ATTACK("attack", "attack", "dir") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.attack_cmd(b.dir(a[0])); }
	},
	GIVE("give", "share", "dir") {//отдать 1/4 энергии
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.give(b.dir(a[0])); }
	},
	SHARE("share", "share", "dir") {//энергию поровну с соседом
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.unif_distrib(b.dir(a[0])); }
	},
	DIVIDE("divide", "birth", "dir", "type") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.multiply(b.dir(a[0]), 0, 0, a[1], false, it); }
	},
	SEED("seed", "birth", "dir", "time", "type") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.multiply(b.dir(a[0]), 1, a[1], a[2], false, it); }
	},
	CHAIN_DIVIDE("chain_divide", "chain", "dir", "type") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.multiply(b.dir(a[0]), 0, 0, a[1], true, it); }
	},
	CHAIN_LINK("chain_link", "chain", "dir") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) { b.add_neighbour_to_chain(b.dir(a[0])); }
	},
	QUEEN_CALL("queen_call", "queen") {//подчиненная призывает соседей к своей королеве
		protected void act(Bot b, ListIterator<IBot> it, int[] a) {
			if (b.queen == 2 && b.queen_bot != null) {
				b.set_queen(b.queen_bot, b, 1);
			}
		}
	},
	QUEEN_BECOME("queen_become", "queen") {//стать королевой (>= 5 связей) или повторить призыв
		protected void act(Bot b, ListIterator<IBot> it, int[] a) {
			if (b.count_chains() >= 5 && b.queen == 0 || b.queen == 1) {
				b.set_queen(b, b, 0);
			}
		}
	},
	QUEEN_ORDER("queen_order", "queen", "command", "distance", "mode (0 — на, 1 — дальше, -1 — ближе)") {
		protected void act(Bot b, ListIterator<IBot> it, int[] a) {
			if (b.queen == 1) {
				b.queen_orders(a[1], a[0], a[2]);
			}
		}
	};
	//
	public static final int FORWARD = 8;//направление «куда смотрю»
	private static final boolean[] on = new boolean[values().length];
	static {
		configure("");
	}
	//
	private final String name;
	private final String category;
	private final String[] args;

	BotAction(String name, String category, String... args) {
		this.name = name;
		this.category = category;
		this.args = args;
	}
	public String id() {
		return name;
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
	public boolean run(Bot body, ListIterator<IBot> iterator, int... args) {
		if (!on[ordinal()] || body.killed != 0) {
			return false;
		}
		act(body, iterator, args);
		return true;
	}
	protected abstract void act(Bot b, ListIterator<IBot> it, int[] a);
	//
	//включение: все, кроме перечисленных через запятую имен
	public static void configure(String off) {
		java.util.Arrays.fill(on, true);
		for (String name : off.split(",")) {
			name = name.trim();
			if (name.isEmpty()) {
				continue;
			}
			BotAction a = by_name(name);
			if (a == null) {
				throw new IllegalArgumentException("нет действия " + name + ", есть: " + names());
			}
			on[a.ordinal()] = false;
		}
	}
	public static void set_enabled(BotAction a, boolean enabled) {
		on[a.ordinal()] = enabled;
	}
	public static BotAction by_name(String name) {
		for (BotAction a : values()) {
			if (a.name.equals(name)) {
				return a;
			}
		}
		return null;
	}
	public static String names() {
		StringBuilder sb = new StringBuilder();
		for (BotAction a : values()) {
			sb.append(sb.length() == 0 ? "" : ", ").append(a.name);
		}
		return sb.toString();
	}
	public String toString() {
		return name;
	}
}
