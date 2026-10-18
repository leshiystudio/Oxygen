package sct;

import java.awt.Color;
import java.io.File;
import java.util.ListIterator;

//Самопроверка без внешних библиотек (в проекте нет Maven/Gradle и JUnit).
//  javac -encoding UTF-8 -d out src/sct/*.java test/sct/*.java
//  java -Djava.awt.headless=true -cp out sct.SelfTest
//Код выхода 0 — все проверки прошли.
public class SelfTest {
	private static int failed = 0;
	private static int passed = 0;

	public static void main(String[] args) throws Exception {
		Presets.set("world_scale=60,60");
		Presets.set("starting_bot_count=300");

		genome_is_independent();
		chain_is_symmetric();
		death_is_counted_once();
		see_org_uses_direction();
		queen_passes_crown();
		kin_clans_by_threshold();
		energy_balance_holds();
		save_load_continues_same_world();
		presets_set_field();
		disabled_action_does_nothing();
		own_brain_runs();
		plants_world();
		plant_child_before_parent();
		mixed_world();
		interactions();

		System.out.println();
		System.out.println("passed: " + passed + ", failed: " + failed);
		System.exit(failed == 0 ? 0 : 1);
	}
	//
	private static void check(String name, boolean ok) {
		if (ok) {
			passed++;
			System.out.println("  ok    " + name);
		}else {
			failed++;
			System.out.println("  FAIL  " + name);
		}
	}
	//
	//пустой мир с одной органикой, без ботов — для точечных сценариев
	private static WorldBio empty_world() {
		WorldBio w = new WorldBio(false);
		for (IBot o : w.objects) {
			w.Map[o.cell_x()][o.cell_y()] = null;
		}
		w.objects.clear();
		return w;
	}
	private static Bot put(WorldBio w, int x, int y, double energy) {
		Bot b = new Bot(x, y, Color.GRAY, energy, 0, w.oxygen_map, w.co2_map, w.org_map, w.Map, w.objects);
		w.objects.add(b);
		w.Map[x][y] = b;
		return b;
	}
	private static Bot multiply(WorldBio w, Bot parent, int rot, boolean chain) {
		ListIterator<IBot> it = w.objects.listIterator(w.objects.indexOf(parent) + 1);
		int before = w.objects.size();
		parent.multiply(rot, 0, 0, 0, chain, it);
		if (w.objects.size() == before) {
			return null;
		}
		int[] p = parent.get_rotate_position(rot);
		return (Bot) w.Map[p[0]][p[1]];
	}
	//
	private static void genome_is_independent() {
		System.out.println("genome copy:");
		int saved = Constant.mutate_chance_percent;
		Constant.mutate_chance_percent = 100;//мутация при каждом делении
		WorldBio w = empty_world();
		Bot parent = put(w, 10, 10, 800);
		int[][] before = new int[Constant.mind_count][];
		for (int i = 0; i < before.length; i++) {
			before[i] = parent.genom_array[i].clone();
		}
		Bot child = multiply(w, parent, 2, false);
		check("child is born", child != null);
		check("mutation of child does not change parent", java.util.Arrays.deepEquals(before, parent.genom_array));
		child.genom_array[0][0] = (child.genom_array[0][0] + 1) % Constant.mind_max_value_command;
		check("rows are not shared", java.util.Arrays.deepEquals(before, parent.genom_array));
		check("child continues parent's genome index", child.genom_current_idx == parent.genom_current_idx);
		Constant.mutate_chance_percent = saved;
	}
	//
	private static void chain_is_symmetric() {
		System.out.println("chain:");
		WorldBio w = empty_world();
		Bot parent = put(w, 20, 20, 800);
		Bot child = multiply(w, parent, 2, true);
		check("parent links to child", parent.has_chain(2));
		check("child links to parent", child != null && child.has_chain(6));
	}
	//
	private static void death_is_counted_once() {
		System.out.println("death:");
		WorldBio w = empty_world();
		Bot b = put(w, 30, 30, 100);
		double org_before = w.org_map[30][30];
		b.die_with_organics();
		double once = w.org_map[30][30] - org_before;
		b.die_with_organics();
		check("second death adds nothing", w.org_map[30][30] - org_before == once);
		check("body and energy return to organics", Math.abs(once - (Constant.energy_for_multiply + 100) / 9.0) < 1e-9);
	}
	//
	private static void see_org_uses_direction() {
		System.out.println("see_org:");
		WorldBio w = empty_world();
		Bot b = put(w, 40, 40, 100);
		b.rotate = 0;
		w.org_map[41][40] = 500;//справа (направление 2)
		w.org_map[39][40] = 0;//слева (направление 6)
		check("looks in the given direction (right, rich)", b.see_org(2, 1) == 0);
		check("looks in the given direction (left, empty)", b.see_org(6, 1) == 1);
	}
	//
	private static void queen_passes_crown() {
		System.out.println("queen:");
		WorldBio w = empty_world();
		Bot q = put(w, 20, 20, 500);
		Bot rich = multiply(w, q, 2, true);//справа
		Bot poor = multiply(w, q, 6, true);//слева
		Bot far = multiply(w, rich, 2, true);//дальше справа
		rich.energy = 900;
		poor.energy = 10;
		q.set_queen(q, q, 0);
		check("queen is set", q.queen == 1);
		check("chained cells become subordinates", rich.queen == 2 && poor.queen == 2 && far.queen == 2 && far.queen_bot == q);
		q.die_with_organics();
		check("richest neighbour becomes queen", rich.queen == 1 && rich.queen_bot == null);
		check("colony obeys the heir", far.queen_bot == rich);
		check("only one queen in colony", poor.queen != 1);
	}
	//
	private static void kin_clans_by_threshold() {
		System.out.println("kin clans:");
		WorldBio w = empty_world();
		Bot a = put(w, 5, 5, 100);
		Bot b = put(w, 7, 5, 100);
		Bot c = put(w, 9, 5, 100);
		for (int i = 0; i < Constant.mind_count; i++) {
			b.genom_array[i] = a.genom_array[i].clone();
			c.genom_array[i] = a.genom_array[i].clone();
		}
		for (int j = 0; j < 3; j++) {//b — близкий родственник
			b.genom_array[0][j] = (a.genom_array[0][j] + 1) % Constant.mind_max_value_command;
		}
		for (int j = 0; j < Constant.max_different_genom + 5; j++) {//c — чужой
			c.genom_array[1][j] = (a.genom_array[1][j] + 1) % Constant.mind_max_value_command;
		}
		KinClans.update(w);
		check("close relative is in the same clan", a.kin_id == b.kin_id);
		check("alien is in another clan", a.kin_id != c.kin_id);
		check("see() agrees: relative", a.count_different_genom(a.genom_array, b.genom_array) <= Constant.max_different_genom);
	}
	//
	private static void energy_balance_holds() {
		System.out.println("energy balance:");
		Presets.seed = 11;
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("balance diff is rounding only (" + Ledger.total_diff + ")", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		Presets.seed = -1;
	}
	//
	private static long state_hash(WorldBio w) {
		long h = w.steps;
		for (IBot o : w.objects) {
			if (o instanceof Bot b && b.killed == 0) {
				h = h * 31 + b.xpos * 1000 + b.ypos;
				h = h * 31 + Double.doubleToLongBits(b.energy);
			}
		}
		for (double[] row : w.org_map) {
			for (double v : row) {
				h = h * 31 + Double.doubleToLongBits(v);
			}
		}
		return h;
	}
	private static void save_load_continues_same_world() throws Exception {
		System.out.println("save/load:");
		Presets.seed = 5;
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		File f = File.createTempFile("oxygen_selftest", ".oxw");
		f.deleteOnExit();
		WorldIO.save(w, f);
		long saved_hash = state_hash(w);
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		long continued = state_hash(w);
		WorldIO.load(w, f);
		check("loaded state equals saved", state_hash(w) == saved_hash);
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		check("loaded world continues the same way", state_hash(w) == continued);
		Presets.seed = -1;
	}
	//
	private static void disabled_action_does_nothing() {
		System.out.println("actions:");
		Presets.seed = 3;
		Constant.bot_actions_off = "attack";
		WorldBio w = new WorldBio(false);
		int eaten = 0;
		for (int i = 0; i < 300; i++) {
			w.step();
			eaten += Ledger.deaths[Ledger.DIE_EATEN];
		}
		check("genome cannot attack when attack is off", eaten == 0);
		Bot b = null;
		for (IBot o : w.objects) {
			if (o instanceof Bot bb && bb.state == 0) {
				b = bb;
				break;
			}
		}
		check("run() of a disabled action returns false", b != null && !BotAction.ATTACK.run(b, null, BotAction.FORWARD));
		Constant.bot_actions_off = "";
		BotAction.configure("");
		boolean thrown = false;
		try {
			BotAction.configure("atack");
		}catch (IllegalArgumentException e) {
			thrown = true;
		}
		check("typo in action name is an error", thrown);
		BotAction.configure("");
		Presets.seed = -1;
	}
	//
	private static void own_brain_runs() {
		System.out.println("own brain:");
		Presets.seed = 4;
		Constant.bot_brain = "simple";
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("world of 'simple' brains lives (" + w.b_count + " bots)", w.b_count > 0);
		check("energy balance holds with own brain", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		Constant.bot_brain = "genome";
		Presets.seed = -1;
	}
	//
	private static void plants_world() {
		System.out.println("plants:");
		Presets.seed = 6;
		Constant.starting_bot_count = 0;
		Constant.starting_plant_count = 400;
		for (int mode : new int[]{sct.plants.PlantConfig.THINK_NEW, sct.plants.PlantConfig.THINK_OLD}) {
			sct.plants.PlantConfig.current = new sct.plants.PlantConfig();
			sct.plants.PlantConfig.current.think_mode = mode;
			WorldBio w = new WorldBio(false);
			for (int i = 0; i < 300; i++) {
				w.step();
			}
			String name = mode == sct.plants.PlantConfig.THINK_NEW ? "think_new" : "think_old";
			check(name + ": world of plants lives (" + w.plant_count + " cells)", w.plant_count > 0);
			check(name + ": energy balance is exact (" + Ledger.total_diff + ")", Ledger.total_diff == 0);
			boolean refused = false;
			try {
				File f = File.createTempFile("oxygen_selftest", ".oxw");
				f.deleteOnExit();
				WorldIO.save(w, f);
			}catch (java.io.IOException e) {
				refused = true;
			}
			check(name + ": saving plants is refused until format 2", refused);
		}
		sct.plants.PlantConfig.current = new sct.plants.PlantConfig();
		Constant.starting_bot_count = 300;
		Constant.starting_plant_count = 0;
		Presets.seed = -1;
	}
	//
	private static void plant_child_before_parent() {
		System.out.println("plant birth:");
		Constant.starting_plant_count = 1;
		WorldBio w = empty_world();
		Constant.starting_plant_count = 0;
		sct.plants.PlantWorld pw = new sct.plants.PlantWorld(w.Map, w.org_map, new double[w.Map.length][w.Map[0].length], w.objects, new sct.plants.PlantConfig());
		Bot before = put(w, 2, 2, 100);
		sct.plants.Plant p = sct.plants.Plant.seedling(pw, 10, 10, sct.plants.ThinkNew.instance);
		p.energy = 1000;
		w.objects.add(p);
		w.Map[10][10] = p;
		Bot after = put(w, 4, 4, 100);
		ListIterator<IBot> it = w.objects.listIterator(w.objects.indexOf(p) + 1);//курсор после родителя, как в ходе мира
		boolean born = sct.plants.PlantAction.REPRODUCE.run(p, it, 1, 2, sct.plants.Plant.ILIVE);
		check("plant child is born", born && w.objects.size() == 4);
		check("child stands right before parent in the list", w.objects.indexOf(p) == 2 && w.objects.get(1) instanceof sct.plants.Plant && w.objects.get(1) != p);
		check("iterator continues after parent", it.hasNext() && it.next() == after && w.objects.get(0) == before);
	}
	//
	private static void mixed_world() {
		System.out.println("mixed world:");
		Presets.seed = 8;
		Constant.starting_plant_count = 300;
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("bots and plants live together (" + w.b_count + " bots, " + w.plant_count + " plant cells)", w.b_count + w.plant_count > 0);
		check("energy balance holds in mixed world", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		Constant.mineral_influx = 1;//приток минералов при растениях — отдельная статья журнала
		w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("energy balance holds with mineral influx on", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		Constant.mineral_influx = -1;
		Constant.starting_plant_count = 0;
		Presets.seed = -1;
	}
	//
	private static void interactions() {
		System.out.println("interactions:");
		WorldBio w = empty_world();
		Population bots = new Population("bots", Species.OXYGEN_BOT, "genome", 0, Color.BLUE);
		Population flora = new Population("plants", Species.PLANT, "think_new", 0, Color.GREEN);
		Population other_bots = new Population("other bots", Species.OXYGEN_BOT, "genome", 0, Color.RED);
		bots.id = 0;
		flora.id = 1;
		other_bots.id = 2;
		Interaction ia = new Interaction(3);
		ia.eat[0][1] = 0.5;//бот ест растение с КПД 0.5
		ia.exchange[0][2] = 2.0;//бот ест чужого бота с обменом x2
		ia.seed_kills[0][2] = false;
		Interaction.current = ia;
		sct.plants.PlantWorld pw = new sct.plants.PlantWorld(w.Map, w.org_map, w.mnr_map, w.objects, new sct.plants.PlantConfig());
		pw.pop = flora;
		//бот ест растение: половина энергии ему, остаток — в почву
		Bot b = put(w, 20, 20, 100);
		b.pop = bots;
		sct.plants.Plant p = sct.plants.Plant.seedling(pw, 21, 20, sct.plants.ThinkNew.instance);//справа (направление 2)
		p.energy = 300;
		w.objects.add(p);
		w.Map[21][20] = p;
		double soil_before = w.mnr_map[21][20];
		Ledger.begin_step();
		b.attack(2);
		check("bot ate plant with efficiency 0.5 (+150)", b.energy == 250 && p.killed == 1);
		check("rest of plant energy went to soil (+150)", w.mnr_map[21][20] - soil_before == 150);
		//обмен x2 — разница в журнале
		Bot alien = put(w, 21, 20, 100);
		alien.pop = other_bots;
		double before = b.energy;
		b.attack(2);
		check("exchange x2 doubles the gain (+200)", b.energy - before == 200);
		check("exchange difference is a ledger item (+100)", Ledger.in_exchange == 100);
		//передача энергии между популяциями закрыта по умолчанию
		Bot neighbour = put(w, 21, 20, 100);
		neighbour.pop = other_bots;
		before = neighbour.energy;
		b.give(2);
		check("no energy sharing between populations by default", neighbour.energy == before);
		//семечко, которому запрещено убивать, гибнет одно
		Bot seed = put(w, 30, 30, 100);
		seed.pop = bots;
		seed.state = 1;
		seed.rotate = 2;
		seed.seed_time = 5;
		Bot target = put(w, 31, 30, 100);
		target.pop = other_bots;
		seed.Update(null);
		check("seed without seed_kills dies alone", seed.killed == 1 && target.killed == 0);
		//росток ест бота: КПД по таблице (1.0), энергия растения целая
		Interaction.current.eat[1][0] = 1.0;
		Bot prey = put(w, 41, 40, 77);
		prey.pop = bots;
		sct.plants.Plant sprout = sct.plants.Plant.seedling(pw, 40, 40, sct.plants.ThinkNew.instance);
		sprout.energy = 10;
		w.objects.add(sprout);
		w.Map[40][40] = sprout;
		boolean ate = sct.plants.PlantAction.EAT.run(sprout, null);
		check("sprout ate a bot (+77)", ate && prey.killed == 1 && sprout.energy == 87);
		Interaction.current = new Interaction(0);
	}
	//
	private static void presets_set_field() {
		System.out.println("presets:");
		int saved = Constant.max_age;
		Presets.set("max_age=777");
		check("--set changes Constant", Constant.max_age == 777);
		Constant.max_age = saved;
	}
}
