package sct.plants;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

//Параметры растений — поля SimConfig из biosim (config.py) с теми же именами и значениями
//по умолчанию. Задаются через --set plant.<имя>=<значение>, набор — --set plant_preset=<имя>.
//Экземпляр, а не static-поля: в этапе 4 у каждой популяции растений будут свои настройки.
public class PlantConfig {
	public static final int THINK_NEW = 0, THINK_OLD = 1;
	public static final int MUT_REPLACE = 0, MUT_DRIFT = 1;
	public static final int BIRTH_PARENT = 0, BIRTH_CHILD = 1;
	//
	public static PlantConfig current = new PlantConfig();
	//
	//--- геном ---
	public int genome_rows = 32;//число генов
	public int genome_cols = 23;//команд в гене
	public int max_number_cmd = 255;//значения команд 0..max_number_cmd-1
	//--- режимы ---
	public int think_mode = THINK_NEW;//мозг ростка: THINK_NEW (гены 32x23) или THINK_OLD (линейный геном)
	public boolean enable_lifecycle = true;//возраст, плата за ход, работа тела
	public boolean enable_eat = true;
	public boolean enable_move = true;
	public boolean move_only_free = true;//двигаться может только росток без родителя
	public boolean enable_corpses = true;//смерть оставляет органику IDEAD, а не сразу пустоту
	public boolean toxic_soil_kills = true;//клетка на почве с энергией > max_energy_around умирает
	public int birth_type_target = BIRTH_PARENT;
	public int max_children_per_step = 3;
	public int energy_transfer_delay = 2;//0 — сразу, 1..2 — тиков в пути
	//--- энергия и здоровье ---
	public int energy_start_value = 300;
	public int health_start_value = 300;
	public int max_energy = 2000;//излишек сверх предела уходит в почву под клеткой
	public int max_health = 1000;//0 — без предела
	public int ilive_cost_step = 2;//плата за ход ростка (и тела без родителя)
	public int body_cost_step = 2;//плата за ход клетки дерева с родителем
	public int health_for_energy = 100;//сколько здоровья тратится, когда энергия кончилась
	public int energy_from_health = 100;//сколько энергии дает это здоровье
	public int max_age = 800;//0 — без смерти от старости
	//--- рождение ---
	public int cost_birth = 150;//цена рождения, теряется
	public int energy_birth_value = 300;//максимум энергии потомку
	public int birth_min_energy = 50;//минимум энергии потомка
	public double birth_share = 0.3;//доля оставшейся у родителя энергии потомку
	public double birth_health_share = 0.5;//>0 — потомок получает долю здоровья родителя
	//--- кланы ---
	public int max_mind_diff_count = 40;//сколько генов может отличаться у «своих»
	//--- мутации ---
	public int mutation_mode = MUT_REPLACE;
	public int drift_max = 8;
	public double percent_mutation_child = 0.05;
	public int mutation_genes_birth = 1;
	public double percent_mutation_am = 0.2;
	public int max_mutation_genes = 3;
	public double mutate_alive_chance = 0.0;
	//--- почва ---
	public int max_energy_around = 1500;//порог токсичности почвы
	public int energy_around_start_value = 100;
	public int organic_around_start_value = 100;//стартовая органика — только если в мире нет ботов Oxygen
	public int organic_mineralize_ppm = 1000;//доля органики (на миллион) в энергию почвы за тик
	public int soil_decay_ppm = 10000;//доля энергии почвы (на миллион), теряемая за тик
	public int soil_shift_max = 500;
	//--- тело ---
	public int soil_draw_root = 10;
	public int soil_draw_leaf = 10;
	public int soil_draw_trans = 15;
	public boolean soil_exhaust_kills = false;
	public boolean body_forward_energy = true;
	public int body_keep_energy = 50;
	public int leaf_sun_base = 30;
	public int leaf_sun_per_empty = 1;
	public int leaf_health_per_empty = 10;
	public int leaf_sun_per_trans = 1;
	public int leaf_health_per_trans = 10;
	public int leaf_sun_per_corpse = -1;
	public int corpse_harm = 1;
	public int trans_leaf_bonus = 25;
	public int trans_leaf_health = 45;
	public int root_feed = 5;
	//--- органика (трупы) ---
	public double corpse_share = 0.3;//доля энергии, остающаяся в трупе (остальное — в почву)
	public int corpse_decay = 50;//сколько энергии труп отдает в органику за тик
	//--- еда ---
	public double eat_efficiency = 1.0;
	public int eat_health_gain = 10;
	//--- мозг THINK_NEW ---
	public int think_threshold = 68;
	public int check_mod = 13;
	public int todo_mod = 8;
	public int type_ilive_max = 64;
	public int type_leaf_max = 76;
	public int type_trans_max = 86;
	public int type_root_max = 96;
	//--- мозг THINK_OLD ---
	public int max_step = 5;
	public int punish_over_max_step = 5000;
	//
	public int genome_size() {
		return genome_rows * genome_cols;
	}
	//
	//наборы biosim (presets.py): original = REFERENCE, остальные — поправки к нему
	private static final Map<String, Object[]> REFERENCE = new LinkedHashMap<String, Object[]>();
	private static void ref(String name, Object value) {
		REFERENCE.put(name, new Object[]{value});
	}
	static {
		ref("max_number_cmd", 255); ref("think_mode", 0); ref("enable_lifecycle", true); ref("enable_eat", true);
		ref("enable_move", true); ref("move_only_free", true); ref("enable_corpses", true); ref("toxic_soil_kills", true);
		ref("birth_type_target", 0); ref("max_children_per_step", 3); ref("energy_transfer_delay", 2);
		ref("energy_start_value", 300); ref("health_start_value", 300); ref("max_energy", 2000); ref("max_health", 1000);
		ref("ilive_cost_step", 5); ref("health_for_energy", 100); ref("energy_from_health", 200); ref("max_age", 0);
		ref("cost_birth", 100); ref("energy_birth_value", 300); ref("birth_min_energy", 50); ref("birth_share", 0.5);
		ref("max_mind_diff_count", 10); ref("mutation_mode", 0); ref("drift_max", 8); ref("percent_mutation_child", 0.5);
		ref("mutation_genes_birth", 1); ref("percent_mutation_am", 0.2); ref("max_mutation_genes", 3); ref("mutate_alive_chance", 0.0);
		ref("max_energy_around", 2000); ref("energy_around_start_value", 100); ref("organic_around_start_value", 1000);
		ref("organic_mineralize_ppm", 1000); ref("soil_shift_max", 500); ref("soil_draw_root", 20); ref("soil_draw_leaf", 10);
		ref("soil_draw_trans", 50); ref("body_forward_energy", true); ref("body_keep_energy", 200); ref("leaf_sun_base", 65);
		ref("leaf_sun_per_empty", 10); ref("leaf_health_per_empty", 10); ref("leaf_sun_per_trans", 1); ref("leaf_health_per_trans", 10);
		ref("leaf_sun_per_corpse", -1); ref("corpse_harm", 1); ref("trans_leaf_bonus", 500); ref("trans_leaf_health", 500);
		ref("root_feed", 5); ref("corpse_share", 0.5); ref("corpse_decay", 10); ref("eat_efficiency", 1.0); ref("eat_health_gain", 10);
		ref("think_threshold", 68); ref("check_mod", 13); ref("todo_mod", 8); ref("type_ilive_max", 64); ref("type_leaf_max", 76);
		ref("type_trans_max", 86); ref("type_root_max", 96); ref("max_step", 5); ref("punish_over_max_step", 5000);
		ref("birth_health_share", 0.0); ref("soil_exhaust_kills", true); ref("body_cost_step", 0); ref("soil_decay_ppm", 0);
	}
	private static final Map<String, String[]> PRESETS = new LinkedHashMap<String, String[]>();
	static {
		PRESETS.put("config", null);//значения по умолчанию (config.py biosim)
		PRESETS.put("original", new String[]{});
		PRESETS.put("noleak", new String[]{"birth_health_share=0.5", "energy_from_health=100", "soil_exhaust_kills=false", "organic_around_start_value=100"});
		PRESETS.put("balanced", new String[]{
			"birth_health_share=0.5", "energy_from_health=100", "soil_exhaust_kills=false", "organic_around_start_value=100",
			"leaf_sun_base=30", "leaf_sun_per_empty=1", "ilive_cost_step=2", "body_cost_step=2", "body_keep_energy=50",
			"cost_birth=150", "birth_share=0.3", "max_age=800",
			"soil_decay_ppm=10000", "max_energy_around=1500", "soil_draw_root=10", "soil_draw_trans=15", "soil_draw_leaf=10",
			"corpse_decay=50", "corpse_share=0.3", "root_feed=5", "trans_leaf_bonus=25", "trans_leaf_health=45",
			"max_mind_diff_count=40", "percent_mutation_child=0.05"});
	}
	public static PlantConfig preset(String name) {
		if (!PRESETS.containsKey(name)) {
			throw new IllegalArgumentException("нет набора растений " + name + ", есть: " + PRESETS.keySet());
		}
		PlantConfig c = new PlantConfig();
		String[] extra = PRESETS.get(name);
		if (extra == null) {
			return c;
		}
		for (Map.Entry<String, Object[]> e : REFERENCE.entrySet()) {
			c.set(e.getKey(), String.valueOf(e.getValue()[0]));
		}
		for (String s : extra) {
			int eq = s.indexOf('=');
			c.set(s.substring(0, eq), s.substring(eq + 1));
		}
		return c;
	}
	//
	public void set(String name, String value) {//поле по имени; для --set plant.<имя>
		try {
			Field f = PlantConfig.class.getField(name);
			Class<?> t = f.getType();
			if (t == int.class) {
				f.setInt(this, Integer.parseInt(value));
			}else if (t == double.class) {
				f.setDouble(this, Double.parseDouble(value));
			}else if (t == boolean.class) {
				f.setBoolean(this, Boolean.parseBoolean(value));
			}else {
				throw new IllegalArgumentException("параметр растений " + name + " задать нельзя");
			}
		}catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("нет параметра растений " + name);
		}catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e.toString());
		}
	}
}
