package sct;

//История метрик по шагам — для живых графиков и выгрузки в CSV.
//Пишет поток симуляции (record, под WorldBio.lock), читает окно (тоже под lock).
//Хранится не больше capacity точек: при переполнении история прореживается вдвое,
//и дальше пишется каждый второй шаг (затем каждый четвертый и т.д.).
public class History {
	public static final String[] names = {
		"bots", "seeds", "queens", "subordinates",
		"energy cells", "organics", "energy total",
		"births", "deaths", "mutations",
		"d: other", "d: age", "d: hunger", "d: organics", "d: eaten", "d: collision", "d: queen lost", "d: removed",
		"unique genomes", "kin clans",
		"oxygen", "co2",
		"ms/step", "balance diff",
		"d: health", "d: branch lost", "d: toxic soil", "d: soil exhausted",
		"plants", "plant corpses", "minerals (soil)",
		"pop 1", "pop 2", "pop 3", "pop 4", "pop 5", "pop 6", "pop 7", "pop 8",//подписи — имена популяций мира (name_populations)
		"no role (gray)", "photosynth (green)", "predators (red)", "recyclers (yellow)", "mixed",
		"sprouts", "leaves", "roots", "trunks (trans)"
	};
	//цвета линий, совпадающие с цветами на карте (null — цвет из палитры графика)
	public static final java.awt.Color[] colors = new java.awt.Color[names.length];
	public static final int BOTS = 0, SEEDS = 1, QUEENS = 2, SUBS = 3, ENERGY_CELLS = 4, ORGANICS = 5, ENERGY_TOTAL = 6,
		BIRTHS = 7, DEATHS = 8, MUTATIONS = 9, DEATH_CAUSE0 = 10, UNIQUE = 18, CLANS = 19, OXYGEN = 20, CO2 = 21,
		MS = 22, BALANCE = 23, DEATH_PLANT0 = 24, PLANTS = 28, CORPSES = 29, SOIL = 30, POP0 = 31, POPS = 8,
		ROLE0 = 39, SPROUTS = 44, LEAVES = 45, ROOTS = 46, TRUNKS = 47;
	static {
		colors[SEEDS] = new java.awt.Color(150, 90, 30);
		colors[ROLE0 + Bot.ROLE_NONE] = new java.awt.Color(150, 150, 150);
		colors[ROLE0 + Bot.ROLE_PHOTO] = new java.awt.Color(60, 220, 60);
		colors[ROLE0 + Bot.ROLE_PREDATOR] = new java.awt.Color(240, 60, 60);
		colors[ROLE0 + Bot.ROLE_RECYCLER] = new java.awt.Color(240, 220, 40);
		colors[ROLE0 + Bot.ROLE_MIXED] = new java.awt.Color(200, 120, 220);
		colors[SPROUTS] = new java.awt.Color(0xDCE6E6);//палитра biosim
		colors[LEAVES] = new java.awt.Color(0x0AB01A);
		colors[ROOTS] = new java.awt.Color(0xE6830A);
		colors[TRUNKS] = new java.awt.Color(0x6E3BFF);
		colors[CORPSES] = new java.awt.Color(0xBE320A);
	}
	public static void name_populations(java.util.List<Population> pops) {//подписи колонок численности популяций
		for (int i = 0; i < POPS; i++) {
			names[POP0 + i] = i < pops.size() ? pops.get(i).name : "pop " + (i + 1);
		}
	}
	public static int death_index(int cause) {//колонка причины смерти: первые 8 — подряд с DEATH_CAUSE0, растительные — с DEATH_PLANT0
		return cause < 8 ? DEATH_CAUSE0 + cause : DEATH_PLANT0 + cause - 8;
	}
	//
	private static final int capacity = 100_000;
	public static double[][] data = new double[names.length][capacity];
	public static int[] step_of = new int[capacity];
	public static int size = 0;
	public static int every = 1;//точка раз в столько шагов (сервер — реже, см. --stats-every); действует с clear()
	private static int stride = 1;
	private static int skip = 0;
	//
	public static void clear() {
		size = 0;
		stride = Math.max(1, every);
		skip = 0;
	}
	//
	public static void record(WorldBio w, double ms) {
		if (++skip < stride) {
			return;
		}
		skip = 0;
		Ledger.sync(w);//полная энергия — на этот шаг, даже если журнал сверяется реже
		if (size == capacity) {//прореживание вдвое
			for (int i = 0; i < capacity / 2; i++) {
				for (double[] s : data) {
					s[i] = s[i * 2];
				}
				step_of[i] = step_of[i * 2];
			}
			size = capacity / 2;
			stride *= 2;
		}
		int bots = 0, seeds = 0, queens = 0, subs = 0, plants = 0, corpses = 0;
		double energy = 0, plant_energy = 0;
		int[] per_pop = new int[POPS];
		int[] roles = new int[5];
		int[] plant_types = new int[7];
		for (IBot o : w.objects) {
			if (!o.alive()) {
				continue;
			}
			Population pp = o.population();
			if (pp != null && pp.id < POPS) {
				per_pop[pp.id]++;
			}
			if (o instanceof sct.plants.Plant p) {
				plant_types[p.type]++;
				if (p.type == sct.plants.Plant.IDEAD) {
					corpses++;
				}else {
					plants++;
				}
				plant_energy += o.ledger_energy();
				continue;
			}
			if (!(o instanceof Bot b)) {
				continue;
			}
			if (b.state == 0) {
				bots++;
				roles[b.role()]++;
			}else {
				seeds++;
			}
			if (b.queen == 1) {
				queens++;
			}else if (b.queen == 2) {
				subs++;
			}
			energy += b.energy;
		}
		double ox = 0, co2 = 0;
		for (int x = 0; x < w.oxygen_map.length; x++) {
			double[] o = w.oxygen_map[x], c = w.co2_map[x];
			for (int y = 0; y < o.length; y++) {
				ox += o[y];
				co2 += c[y];
			}
		}
		int i = size;
		data[BOTS][i] = bots;
		data[SEEDS][i] = seeds;
		data[QUEENS][i] = queens;
		data[SUBS][i] = subs;
		data[ENERGY_CELLS][i] = energy;
		data[ENERGY_TOTAL][i] = Ledger.total;
		double soil = w.plants != null ? Ledger.sum(w.mnr_map) : 0;//минералы считаются энергией, когда есть растения
		data[ORGANICS][i] = Ledger.total - energy - (double)(bots + seeds) * Constant.energy_for_multiply - plant_energy - soil;
		data[PLANTS][i] = plants;
		data[CORPSES][i] = corpses;
		data[SOIL][i] = soil;
		for (int r = 0; r < roles.length; r++) {
			data[ROLE0 + r][i] = roles[r];
		}
		data[SPROUTS][i] = plant_types[sct.plants.Plant.ILIVE];
		data[LEAVES][i] = plant_types[sct.plants.Plant.LEAF];
		data[ROOTS][i] = plant_types[sct.plants.Plant.ROOT];
		data[TRUNKS][i] = plant_types[sct.plants.Plant.TRANS];
		for (int k = 0; k < POPS; k++) {
			data[POP0 + k][i] = per_pop[k];
		}
		data[BIRTHS][i] = Ledger.births;
		data[DEATHS][i] = Ledger.deaths_total();
		data[MUTATIONS][i] = Ledger.mutations;
		for (int c = 0; c < Ledger.die_names.length; c++) {
			data[death_index(c)][i] = Ledger.deaths[c];
		}
		data[UNIQUE][i] = KinClans.unique_genomes;
		data[CLANS][i] = KinClans.clans;
		data[OXYGEN][i] = ox;
		data[CO2][i] = co2;
		data[MS][i] = ms;
		data[BALANCE][i] = Ledger.total_diff;
		step_of[i] = w.steps;
		size++;
	}
	//
	public static String csv() {//вся история в CSV
		StringBuilder sb = new StringBuilder("step");
		for (String n : names) {
			sb.append(',').append(n.replace(',', ' '));
		}
		sb.append('\n');
		for (int i = 0; i < size; i++) {
			sb.append(step_of[i]);
			for (double[] s : data) {
				sb.append(',').append(s[i]);
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
