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
	public static final int DIE_HEALTH = 8;//растение: кончилось здоровье
	public static final int DIE_LOST = 9;//растение: тело потеряло все цели передачи энергии
	public static final int DIE_TOXIC = 10;//растение: токсичная почва
	public static final int DIE_SOIL = 11;//растение: почва под телом истощена
	public static final String[] die_names = {"other", "age", "hunger", "organics", "eaten", "collision", "queen lost", "removed",
		"health", "branch lost", "toxic soil", "soil exhausted"};
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
	//растения (журнал biosim)
	public static double in_plant_sun;//фотосинтез листьев
	public static double in_plant_health;//здоровье, переведенное в энергию
	public static double out_plant_step;//плата за ход
	public static double out_plant_punish;//штраф THINK_OLD
	public static double out_plant_birth;//цена рождения
	public static double out_soil_decay;//вымывание энергии почвы
	public static double in_mineral_influx;//случайный приток минералов, если включен при растениях (mineral_influx)
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
		in_plant_sun = 0;
		in_plant_health = 0;
		out_plant_step = 0;
		out_plant_punish = 0;
		out_plant_birth = 0;
		out_soil_decay = 0;
		in_mineral_influx = 0;
		births = 0;
		mutations = 0;
		java.util.Arrays.fill(deaths, 0);
	}
	//
	public static double in_step() {
		return in_sun + in_negative + in_plant_sun + in_plant_health + in_mineral_influx;
	}
	public static double out_step() {
		return out_life + out_move + out_cap + out_birth_fail + out_org_clamp + out_org_other
			+ out_plant_step + out_plant_punish + out_plant_birth + out_soil_decay;
	}
	//
	public static double measure(WorldBio w) {//фактическая полная энергия мира
		double sum = 0;
		for (IBot o : w.objects) {//у каждого вида свой учет: у бота — энергия и тело
			sum += o.ledger_energy();
		}
		for (double[] row : w.org_map) {
			for (double v : row) {
				sum += v;
			}
		}
		if (w.plants != null) {//минералы — почва растений: при растениях это энергия
			sum += sum(w.mnr_map);
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
	public static double sum(double[][] m) {
		double s = 0;
		for (double[] row : m) {
			for (double v : row) {
				s += v;
			}
		}
		return s;
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
