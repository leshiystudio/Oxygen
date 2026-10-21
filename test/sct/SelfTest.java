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
		world_settings();
		neuro();
		leaves_use_co2();
		restart_and_elite();
		video_recorder();

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
	//видео «последний час»: кодировщик подменен — ffmpeg не нужен
	private static void video_recorder() throws Exception {
		System.out.println("video recorder:");
		WorldBio w = new WorldBio(false);
		VideoRecorder r = new VideoRecorder(w, 0, 2);
		java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
		java.util.List<String> starts = new java.util.ArrayList<String>();
		r.encoder = new VideoRecorder.Encoder() {
			public java.io.OutputStream start(int width, int height) {
				starts.add(width + "x" + height);
				return sink;
			}
			public void stop() {
			}
		};
		int ww = Constant.world_scale[0], wh = Constant.world_scale[1];
		int captured = 0;
		for (int i = 0; i < 3; i++) {
			w.step();
			if (r.capture()) {
				captured++;
			}
			r.write_next(0);
		}
		check("a frame per capture while the world runs (" + r.frames + ")", captured == 3 && r.frames == 3);
		check("raw bgr0 frames of world size", sink.size() == 3 * ww * wh * 4 && starts.equals(java.util.List.of(ww + "x" + wh)));
		int[] px = ((java.awt.image.DataBufferInt) new PixelRenderer().render(w, 0, 2, 1, true, false).getRaster().getDataBuffer()).getData();
		byte[] b = sink.toByteArray();
		boolean same = true;
		for (int i = 0, o = 2 * ww * wh * 4; i < px.length; i++, o += 4) {//последний кадр: байты B G R 0
			same &= b[o] == (byte) px[i] && b[o + 1] == (byte) (px[i] >> 8) && b[o + 2] == (byte) (px[i] >> 16) && b[o + 3] == 0;
		}
		check("pixels are the rendered world, bgr0", same);
		check("no new frame on pause (same step)", !r.capture());
		r.offer(new VideoRecorder.Frame(4, 2, new int[8]));
		r.offer(new VideoRecorder.Frame(4, 2, new int[8]));
		r.offer(new VideoRecorder.Frame(4, 2, new int[8]));
		check("full queue drops the oldest frame, capture never waits", r.dropped == 1);
		r.write_next(0);
		check("new frame size restarts the encoder", starts.size() == 2 && starts.get(1).equals("4x2"));
		check("views parsed", VideoRecorder.parse_views("0_2, 8_0").size() == 2 && VideoRecorder.parse_views("").isEmpty());
		boolean bad = true;
		for (String v : new String[]{"0-2", "99_0", "0_99", "0_2,0_2", "../0_2"}) {
			try {
				VideoRecorder.parse_views(v);
				bad = false;
			}catch (IllegalArgumentException e) {
				//так и должно быть
			}
		}
		check("wrong views are errors", bad);
		VideoRecorder.record_steps = 3;//кадр по шагам: из конца шага, ровно на каждом 3-м
		VideoRecorder by_steps = new VideoRecorder(w, 0, 0);
		java.io.ByteArrayOutputStream sink2 = new java.io.ByteArrayOutputStream();
		by_steps.encoder = new VideoRecorder.Encoder() {
			public java.io.OutputStream start(int width, int height) {
				return sink2;
			}
			public void stop() {
			}
		};
		java.util.List<Integer> at = new java.util.ArrayList<Integer>();
		w.after_step = () -> {
			int before = by_steps.frames + by_steps.queued();
			by_steps.on_step();
			if (by_steps.frames + by_steps.queued() > before) {
				at.add(w.steps);
			}
		};
		int start = w.steps;
		for (int i = 0; i < 10; i++) {
			w.step();
			by_steps.write_next(0);
		}
		w.after_step = null;
		boolean every3 = !at.isEmpty();
		for (int s : at) {
			every3 &= s % 3 == 0;
		}
		check("record by steps: a frame exactly every 3rd step (" + at + ")", every3 && at.size() == (start + 10) / 3 - start / 3 && by_steps.frames == at.size());
		VideoRecorder.window_steps = 1000;
		check("window by steps: segments of 60 frames cover it", VideoRecorder.segments() == 6 && VideoRecorder.segment_frames() == 60);
		VideoRecorder.window_steps = 0;
		check("default window by steps — 3600 frames", VideoRecorder.segments() == 60);
		VideoRecorder.record_steps = 0;
		check("by time: a segment is a minute of frames", VideoRecorder.segment_frames() == 60_000 / VideoRecorder.record_ms && VideoRecorder.segments() == VideoRecorder.minutes);
		check("only playlist and segments are served", VideoRecorder.valid_file("index.m3u8") && VideoRecorder.valid_file("seg_00012.ts")
			&& !VideoRecorder.valid_file("..") && !VideoRecorder.valid_file("seg_1.ts") && !VideoRecorder.valid_file("seg_00001.ts/../x")
			&& !VideoRecorder.valid_file("index.m3u8.tmp") && !VideoRecorder.valid_file(""));
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
			if (o.alive()) {
				h = h * 31 + o.cell_x() * 1000 + o.cell_y();
				h = h * 31 + Double.doubleToLongBits(o.ledger_energy());
				h = h * 31 + o.species().ordinal();
			}
		}
		for (double[] row : w.mnr_map) {
			for (double v : row) {
				h = h * 31 + Double.doubleToLongBits(v);
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
		//смешанный мир (формат 2): боты и растения, популяции, генератор растений
		Presets.seed = 9;
		Constant.starting_plant_count = 200;
		w = new WorldBio(false);
		Constant.starting_plant_count = 0;
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		f = File.createTempFile("oxygen_selftest", ".oxw");
		f.deleteOnExit();
		WorldIO.save(w, f);
		saved_hash = state_hash(w);
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		continued = state_hash(w);
		WorldIO.load(w, f);
		check("mixed world: loaded state equals saved", state_hash(w) == saved_hash);
		for (int i = 0; i < 50; i++) {
			w.step();
		}
		check("mixed world: continues the same way after load", state_hash(w) == continued);
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
		Ledger.check_every = 7;//как на сервере: сверка и точки истории реже, расхождение копится между сверками
		History.every = 10;
		w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("history every 10 steps (" + History.size + " points)", History.size == 30 && History.step_of[29] == 300);
		check("energy balance holds with rare checks", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		check("history total is measured on its step", History.data[History.ENERGY_TOTAL][29] == Ledger.measure(w));
		Ledger.check_every = 1;
		History.every = 1;
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
	private static void world_settings() {
		System.out.println("new world settings:");
		WorldSettings ws = WorldSettings.capture();
		ws.seed = 12;
		ws.populations.clear();
		Population b = new Population("hunters", Species.OXYGEN_BOT, "simple", 200, Color.RED);
		b.actions_off("seed,give");
		Population p = new Population("forest", Species.PLANT, "think_old", 150, Color.GREEN);
		p.plant_config = sct.plants.PlantConfig.preset("original");
		p.plant_config.leaf_sun_base = 42;
		ws.populations.add(b);
		ws.populations.add(p);
		ws.interaction = new Interaction(2);
		ws.interaction.eat[0][1] = 0.25;
		ws.interaction.exchange[1][0] = 1.5;
		File f;
		WorldSettings back;
		try {
			f = File.createTempFile("oxygen_selftest", ".properties");
			f.deleteOnExit();
			ws.save(f);
			back = WorldSettings.load(f);
		}catch (java.io.IOException e) {
			check("settings save/load: " + e, false);
			return;
		}
		check("populations survive save/load", back.populations.size() == 2 && back.populations.get(0).brain.equals("simple")
			&& back.populations.get(1).brain.equals("think_old") && back.populations.get(1).count == 150);
		check("disabled actions survive", !back.populations.get(0).on(BotAction.SEED.ordinal()) && back.populations.get(0).on(BotAction.ATTACK.ordinal()));
		check("plant parameters survive", back.populations.get(1).plant_config.leaf_sun_base == 42 && back.populations.get(1).plant_config.max_age == 0);
		check("interaction survives", back.interaction.eat[0][1] == 0.25 && back.interaction.exchange[1][0] == 1.5);
		WorldBio w = new WorldBio(false);
		back.apply(w);
		int bots = 0, plants = 0;
		for (IBot o : w.objects) {
			if (o.population() == w.populations.get(0)) {
				bots++;
			}else if (o.population() == w.populations.get(1)) {
				plants++;
			}
		}
		check("world is created from settings (" + bots + " bots, " + plants + " plants)", bots == 200 && plants == 150);
		check("interaction table is active", Interaction.current.eat[0][1] == 0.25);
		for (int i = 0; i < 100; i++) {
			w.step();
		}
		check("this world runs, balance holds", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		Interaction.current = new Interaction(0);
		Presets.seed = -1;
	}
	//
	private static void neuro() {
		System.out.println("neuro:");
		Presets.seed = 21;
		Constant.bot_brain = Brains.NEURO;
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < 300; i++) {
			w.step();
		}
		check("world of neuro bots lives (" + w.b_count + " bots)", w.b_count > 0);
		check("energy balance holds with neuro bots", Math.abs(Ledger.total_diff) < 1e-9 * Math.max(1, Ledger.total));
		//наследование сети
		Bot parent = null;
		for (IBot o : w.objects) {
			if (o instanceof Bot b && b.state == 0) {
				parent = b;
				break;
			}
		}
		sct.neuro.NeuroBrain<Bot> nb = (sct.neuro.NeuroBrain<Bot>) parent.brain;
		double saved_chance = nb.c.mutation_chance;
		nb.c.mutation_chance = 0;
		sct.neuro.NeuroBrain<Bot> same = (sct.neuro.NeuroBrain<Bot>) nb.child(parent);
		nb.c.mutation_chance = 1;
		nb.c.mutation_rate = 1;
		sct.neuro.NeuroBrain<Bot> mutated = (sct.neuro.NeuroBrain<Bot>) nb.child(parent);
		nb.c.mutation_chance = saved_chance;
		nb.c.mutation_rate = 0.05;
		check("child without mutation has the same network", nb.net.distance(same.net) == 0 && same.net != nb.net);
		check("mutated child network differs", nb.net.distance(mutated.net) > 0);
		//только включенные действия: оставить один фотосинтез
		Constant.bot_actions_off = "turn,set_dir,random_dir,move,recycle,attack,give,share,divide,seed,chain_divide,chain_link,queen_call,queen_become,queen_order";
		w = new WorldBio(false);
		int attackers = 0, eaten = 0;
		for (int i = 0; i < 100; i++) {
			w.step();
			eaten += Ledger.deaths[Ledger.DIE_EATEN];
		}
		for (IBot o : w.objects) {
			if (o instanceof Bot b && b.c_red == 255 && b.c_green == 0) {
				attackers++;//go_red() после атаки — следа быть не должно
			}
		}
		check("neuro bot uses only enabled actions (no attacks)", eaten == 0 && attackers == 0);
		Constant.bot_actions_off = "";
		//смешанный мир нейро-ботов и нейро-растений: сохранение версии 3
		Constant.starting_plant_count = 200;
		Constant.plant_brain = Brains.NEURO;
		w = new WorldBio(false);
		for (int i = 0; i < 40; i++) {
			w.step();
		}
		try {
			File f = File.createTempFile("oxygen_selftest", ".oxw");
			f.deleteOnExit();
			WorldIO.save(w, f);
			long saved = state_hash(w);
			for (int i = 0; i < 40; i++) {
				w.step();
			}
			long continued = state_hash(w);
			WorldIO.load(w, f);
			check("neuro world: loaded state equals saved", state_hash(w) == saved);
			for (int i = 0; i < 40; i++) {
				w.step();
			}
			check("neuro world: continues the same way after load (weights saved)", state_hash(w) == continued);
		}catch (java.io.IOException e) {
			check("neuro world save/load: " + e, false);
		}
		//настройки сети в файле настроек
		WorldSettings ws = WorldSettings.capture();
		ws.populations.get(0).neuro_config().hidden = "24,8";
		ws.populations.get(0).neuro_config().choice = "softmax";
		try {
			File f = File.createTempFile("oxygen_selftest", ".properties");
			f.deleteOnExit();
			ws.save(f);
			WorldSettings back = WorldSettings.load(f);
			check("network settings survive save/load", back.populations.get(0).neuro_config().hidden.equals("24,8")
				&& back.populations.get(0).neuro_config().choice.equals("softmax"));
		}catch (java.io.IOException e) {
			check("network settings save/load: " + e, false);
		}
		Constant.bot_brain = "genome";
		Constant.plant_brain = "";
		Constant.starting_plant_count = 0;
		Presets.seed = -1;
	}
	//
	private static void leaves_use_co2() {
		System.out.println("absorb_co2 action:");
		WorldBio w = empty_world();
		sct.plants.PlantConfig cfg = new sct.plants.PlantConfig();
		sct.plants.PlantWorld pw = new sct.plants.PlantWorld(w.Map, w.org_map, w.mnr_map, w.co2_map, w.oxygen_map, w.objects, cfg);
		sct.plants.Plant sprout = sct.plants.Plant.seedling(pw, 10, 10, sct.plants.ThinkNew.instance);
		w.objects.add(sprout);
		w.Map[10][10] = sprout;
		double co2_before = Ledger.sum(w.co2_map), ox_before = w.oxygen_map[10][10];
		int e_before = sprout.energy;
		Ledger.begin_step();
		boolean ok = sct.plants.PlantAction.ABSORB_CO2.run(sprout, null);
		check("sprout absorbs CO2 around", ok && Ledger.sum(w.co2_map) < co2_before);
		check("and gets energy (ledger item)", sprout.energy > e_before && Ledger.in_plant_co2 == sprout.energy - e_before);
		check("and releases oxygen", w.oxygen_map[10][10] > ox_before);
		//без карт газов и без CO2 — «не удалось», без ошибок
		sct.plants.PlantWorld no_gas = new sct.plants.PlantWorld(w.Map, w.org_map, w.mnr_map, w.objects, cfg);
		sct.plants.Plant s2 = sct.plants.Plant.seedling(no_gas, 20, 20, sct.plants.ThinkNew.instance);
		w.Map[20][20] = s2;
		boolean no_maps = sct.plants.PlantAction.ABSORB_CO2.run(s2, null);
		for (double[] row : w.co2_map) {
			java.util.Arrays.fill(row, 0);
		}
		boolean no_co2 = sct.plants.PlantAction.ABSORB_CO2.run(sprout, null);
		check("no gas maps or no CO2 — action fails, no crash", !no_maps && !no_co2);
		//think_new выбирает действие только при todo_mod >= 9; галочка выключает
		Presets.seed = 31;
		Constant.starting_bot_count = 0;
		Constant.starting_plant_count = 300;
		sct.plants.PlantConfig.current = new sct.plants.PlantConfig();
		sct.plants.PlantConfig.current.todo_mod = 9;
		WorldBio world = new WorldBio(false);
		double gained = 0;
		for (int i = 0; i < 200; i++) {
			world.step();
			gained += Ledger.in_plant_co2;
		}
		check("think_new with todo_mod=9 uses absorb_co2 (" + (long) gained + " energy)", gained > 0);
		check("energy balance is exact with absorb_co2 (" + Ledger.total_diff + ")", Ledger.total_diff == 0);
		Constant.plant_actions_off = "absorb_co2";
		world = new WorldBio(false);
		gained = 0;
		for (int i = 0; i < 100; i++) {
			world.step();
			gained += Ledger.in_plant_co2;
		}
		check("disabled absorb_co2 gives nothing", gained == 0);
		Constant.plant_actions_off = "";
		sct.plants.PlantConfig.current = new sct.plants.PlantConfig();
		Constant.starting_bot_count = 300;
		Constant.starting_plant_count = 0;
		Presets.seed = -1;
	}
	//
	private static void presets_set_field() {
		System.out.println("presets:");
		int saved = Constant.max_age;
		Presets.set("max_age=777");
		check("--set changes Constant", Constant.max_age == 777);
		Constant.max_age = saved;
	}
	//
	//перезапуск при вымирании и клоны лучших
	private static void kill_all_of(WorldBio w, Species sp) {
		for (IBot o : w.objects) {
			if (o.species() == sp && o.alive()) {
				o.die(Ledger.DIE_REMOVED);//растение оставляет труп: он в мире, но не живой — вымирание его не считает
			}
		}
	}
	private static void restart_and_elite() throws Exception {
		System.out.println("restart when extinct, elite:");
		Presets.seed = 11;
		Constant.restart_mode = "off";
		WorldBio w = new WorldBio(false);
		w.step();
		kill_all_of(w, Species.OXYGEN_BOT);
		w.step();
		check("restart off: an empty world stays empty", w.restarts == 0 && w.objects.isEmpty());

		Constant.restart_forced = 1;//принудительный: без вымирания, через restart_steps шагов попытки
		Constant.restart_steps = 5;
		Constant.elite = 1;
		Constant.elite_size = 10;
		Constant.elite_fields = "lifetime";
		w = new WorldBio(false);
		w.step();
		Bot hero = null;
		for (IBot o : w.objects) {//долгожитель с узнаваемым геномом — должен перейти в новый мир
			if (o instanceof Bot b) {
				hero = b;
				break;
			}
		}
		hero.life_steps = 1_000_000;
		java.util.Arrays.fill(hero.genom_array[0], 44);
		for (int i = 0; i < 3; i++) {
			w.step();
		}
		check("forced restart: not before restart_steps (step " + w.steps + ")", w.restarts == 0 && w.steps == 4);
		w.step();
		boolean carried = false;
		for (IBot o : w.objects) {
			carried |= o instanceof Bot b && b.genom_array[0][0] == 44 && b.genom_array[0][63] == 44;
		}
		check("forced restart after restart_steps, the living best are carried", w.restarts == 1 && w.steps == 0 && carried);
		for (int i = 0; i < 10; i++) {
			w.step();
		}
		check("forced restart repeats every restart_steps of a try (restarts " + w.restarts + ")", w.restarts == 3 && w.steps == 0);
		Constant.restart_forced = 0;
		for (int i = 0; i < 10; i++) {
			w.step();
		}
		check("forced restart off: the world goes on", w.restarts == 3 && w.steps == 10);
		boolean zero_steps = false;
		try {
			Presets.set("restart_steps=0");
		}catch (IllegalArgumentException e) {
			zero_steps = true;
		}
		check("restart_steps must be at least 1", zero_steps && Constant.restart_steps == 5);
		Constant.restart_steps = 100000;
		Constant.elite = 0;

		Constant.restart_mode = "all";
		Constant.elite = 1;
		Constant.elite_size = 10;
		Constant.elite_fields = "lifetime";
		w = new WorldBio(false);
		w.step();
		java.util.List<Bot> marked = new java.util.ArrayList<Bot>();
		for (IBot o : w.objects) {//трое долгожителей с узнаваемым геномом — должны попасть в архив
			if (o instanceof Bot b && marked.size() < 3) {
				b.life_steps = 1_000_000;
				java.util.Arrays.fill(b.genom_array[0], 40 + marked.size());
				marked.add(b);
			}
		}
		long series = w.series_started;
		kill_all_of(w, Species.OXYGEN_BOT);
		w.step();
		check("restart all: the world is created again (restarts " + w.restarts + ")", w.restarts == 1 && w.objects.size() == Constant.starting_bot_count);
		check("the series start is kept over the restart", w.series_started == series);
		int clones = 0, from_archive = 0;
		java.util.Set<String> archive = new java.util.HashSet<String>();
		for (IBot o : w.objects) {
			Bot b = (Bot) o;
			String g = java.util.Arrays.deepToString(b.genom_array);
			archive.add(g);
			if (b.genom_array[0][0] >= 40 && b.genom_array[0][0] <= 42 && b.genom_array[0][63] == b.genom_array[0][0]) {
				clones++;
			}
		}
		from_archive = archive.size();
		check("new bots are exact clones of " + from_archive + " archived (elite_size 10)", from_archive <= 10);
		check("the best by lifetime are cloned (" + clones + " of " + w.objects.size() + " = 3 of 10)", clones == 3 * Constant.starting_bot_count / 10);

		java.io.File f = java.io.File.createTempFile("oxygen-restart", ".oxw");
		WorldIO.save(w, f);
		WorldBio w2 = new WorldBio(false);
		WorldIO.load(w2, f);
		f.delete();
		check("restarts and series start survive save/load", w2.restarts == 1 && w2.series_started == series);

		w.newPopulation();//кнопка Again / New world — новая серия
		check("Again starts a new series", w.restarts == 0 && w.series_started >= series);

		Constant.restart_mode = "any";
		Constant.starting_plant_count = 200;
		w = new WorldBio(false);
		w.step();
		for (IBot o : w.objects) {
			if (o instanceof sct.plants.Plant p) {
				java.util.Arrays.fill(p.genome, (byte) 7);
				p.life_steps = 1_000_000;
			}
		}
		kill_all_of(w, Species.PLANT);
		w.step();
		check("restart any: plants extinct, bots alive — restarted", w.restarts == 1);
		boolean plant_clones = true;
		int plants = 0;
		for (IBot o : w.objects) {
			if (o instanceof sct.plants.Plant p) {
				plants++;
				for (byte v : p.genome) {
					plant_clones &= v == 7;
				}
			}
		}
		check("plants are cloned from the archive too (" + plants + " seedlings)", plants > 0 && plant_clones);

		Constant.restart_mode = "off";
		Constant.elite = 0;
		Constant.elite_size = 100;
		Constant.elite_fields = "lifetime,children";
		Constant.starting_plant_count = 0;
		Presets.seed = -1;
		boolean bad = false;
		try {
			Presets.set("restart_mode=sometimes");
		}catch (IllegalArgumentException e) {
			bad = true;
		}
		boolean bad_field = false;
		try {
			Presets.set("elite_fields=lifetime,beauty");
		}catch (IllegalArgumentException e) {
			bad_field = true;
		}
		check("wrong restart_mode / elite field is an error", bad && bad_field);
	}

}
