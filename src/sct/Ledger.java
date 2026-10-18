package sct;

//Журнал энергии и счетчики событий за шаг.
//
//Полная энергия мира = энергия живых клеток + тела живых клеток (energy_for_multiply на каждую:
//столько стоит рождение и столько возвращается в органику при смерти) + вся органика.
//Приход — только фотосинтез. Расход — плата за жизнь и движение, потолок энергии, обрезка органики.
//Все остальное (деление, атака, передача, цепочки, переработка органики, смерть) — переходы
//между частями мира, они полную энергию не меняют. Каждый шаг фактическая энергия сверяется
//с ожидаемой: расхождение показывает, где энергия пропадает или берется из ничего.
//
//Все поля меняет только поток симуляции (под WorldBio.lock).
public class Ledger {
	//
	//причины смерти
	public static final int DIE_OTHER = 0;
	public static final int DIE_AGE = 1;//старость
	public static final int DIE_HUNGER = 2;//кончилась энергия (в том числе при делении)
	public static final int DIE_ORGANICS = 3;//переизбыток органики в клетке
	public static final int DIE_EATEN = 4;//съеден
	public static final int DIE_COLLISION = 5;//столкновение с семечком
	public static final int DIE_QUEEN_LOST = 6;//подчиненная без королевы
	public static final int DIE_REMOVED = 7;//удалена инструментом
	public static final String[] die_names = {"other", "age", "hunger", "organics", "eaten", "collision", "queen lost", "removed"};
	//
	//за текущий шаг
	public static double in_sun;//фотосинтез
	public static double out_life;//плата за жизнь
	public static double out_move;//плата за движение
	public static double out_cap;//срезано потолком энергии 1000/2000
	public static double out_birth_fail;//цена деления, когда потомок не родился (родитель погиб)
	public static double in_negative;//отрицательная энергия погибшей клетки, списанная при смерти
	public static double out_org_clamp;//органика сверх org_die_level, срезанная при смерти
	public static double out_org_other;//органика, убранная фотосинтезом и семечками в мертвой зоне
	public static int births;
	public static int mutations;
	public static int[] deaths = new int[die_names.length];
	//
	//итоги
	public static double total = Double.NaN;//фактическая полная энергия после шага
	public static double step_diff;//расхождение за шаг
	public static double total_diff;//накопленное расхождение
	//
	public static void begin_step() {
		in_sun = 0;
		out_life = 0;
		out_move = 0;
		out_cap = 0;
		out_birth_fail = 0;
		in_negative = 0;
		out_org_clamp = 0;
		out_org_other = 0;
		births = 0;
		mutations = 0;
		java.util.Arrays.fill(deaths, 0);
	}
	//
	public static double in_step() {
		return in_sun + in_negative;
	}
	public static double out_step() {
		return out_life + out_move + out_cap + out_birth_fail + out_org_clamp + out_org_other;
	}
	//
	public static double measure(WorldBio w) {//фактическая полная энергия мира
		double sum = 0;
		for (Bot b : w.objects) {
			if (b.killed == 0) {
				sum += b.energy + Constant.energy_for_multiply;
			}
		}
		for (double[] row : w.org_map) {
			for (double v : row) {
				sum += v;
			}
		}
		return sum;
	}
	//
	public static void reset(WorldBio w) {//новый мир или загрузка: отсчет заново
		total = measure(w);
		step_diff = 0;
		total_diff = 0;
	}
	//
	public static void end_step(WorldBio w) {
		double expected = total + in_step() - out_step();
		total = measure(w);
		step_diff = total - expected;
		total_diff += step_diff;
	}
	//
	public static void die(int cause) {
		deaths[cause]++;
	}
	public static int deaths_total() {
		int s = 0;
		for (int d : deaths) {
			s += d;
		}
		return s;
	}
}
