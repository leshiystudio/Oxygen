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
		"plants", "plant corpses", "minerals (soil)"
	};
	public static final int BOTS = 0, SEEDS = 1, QUEENS = 2, SUBS = 3, ENERGY_CELLS = 4, ORGANICS = 5, ENERGY_TOTAL = 6,
		BIRTHS = 7, DEATHS = 8, MUTATIONS = 9, DEATH_CAUSE0 = 10, UNIQUE = 18, CLANS = 19, OXYGEN = 20, CO2 = 21,
		MS = 22, BALANCE = 23, DEATH_PLANT0 = 24, PLANTS = 28, CORPSES = 29, SOIL = 30;
	public static int death_index(int cause) {//колонка причины смерти: первые 8 — подряд с DEATH_CAUSE0, растительные — с DEATH_PLANT0
		return cause < 8 ? DEATH_CAUSE0 + cause : DEATH_PLANT0 + cause - 8;
	}
	//
	private static final int capacity = 100_000;
	public static double[][] data = new double[names.length][capacity];
	public static int[] step_of = new int[capacity];
	public static int size = 0;
	private static int stride = 1;
	private static int skip = 0;
	//
	public static void clear() {
		size = 0;
		stride = 1;
		skip = 0;
	}
	//
	public static void record(WorldBio w, double ms) {
		if (++skip < stride) {
			return;
		}
		skip = 0;
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
		for (IBot o : w.objects) {
			if (!o.alive()) {
				continue;
			}
			if (o instanceof sct.plants.Plant p) {
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
