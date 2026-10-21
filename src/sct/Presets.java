package sct;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//Наборы параметров и аргументы командной строки.
//
//  --preset <имя>        набор из PRESETS (по умолчанию default — значения из Constant)
//  --set имя=значение    поправка любого public static поля Constant поверх набора, можно повторять
//  --seed <число>        зерно: одно и то же число — один и тот же мир
//  --w, --h, --bots      размер мира и число стартовых ботов
//  --theme dark|light    тема интерфейса
//
//Пример: --preset small --seed 7 --set max_age=1000 --set pht_coeff=90
public class Presets {
	public static final Map<String, String[]> PRESETS = new LinkedHashMap<String, String[]>();
	static {
		PRESETS.put("default", new String[]{});
		PRESETS.put("small", new String[]{"world_scale=200,200", "starting_bot_count=1500"});//быстрый мир для проверок
		PRESETS.put("big", new String[]{"world_scale=600,400", "starting_bot_count=10000"});
	}
	//
	public static long seed = -1;//меньше нуля — случайное
	public static String preset = "default";
	//
	public static void apply_args(String[] args) {//ошибка в аргументах — сообщение и выход
		try {
			apply_args_or_throw(args);
		}catch (IllegalArgumentException e) {
			System.err.println("Ошибка: " + e.getMessage());
			System.exit(2);
		}
	}
	private static void apply_args_or_throw(String[] args) {
		List<String> sets = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
				case "--preset": preset = need(args, ++i, a); break;
				case "--set": sets.add(need(args, ++i, a)); break;
				case "--seed": seed = Long.parseLong(need(args, ++i, a)); break;
				case "--w": sets.add("world_scale=" + need(args, ++i, a) + "," + Constant.world_scale[1]); break;
				case "--h": sets.add("world_scale=" + Constant.world_scale[0] + "," + need(args, ++i, a)); break;
				case "--bots": sets.add("starting_bot_count=" + need(args, ++i, a)); break;
				case "--theme": Theme.dark = !"light".equals(need(args, ++i, a)); break;
				case "--help": case "-h": help(); System.exit(0); break;
				default: fail("неизвестный аргумент " + a + " (--help — список)");
			}
		}
		String[] p = PRESETS.get(preset);
		if (p == null) {
			fail("нет набора " + preset + ", есть: " + PRESETS.keySet());
		}
		for (String s : p) {
			set(s);
		}
		for (String s : sets) {//--w и --h применяются по очереди: каждая берет текущий второй размер
			set(s);
		}
	}
	//
	public static void apply_seed(WorldBio w) {//все генераторы мира — от одного зерна
		if (seed < 0) {
			return;
		}
		Bot.rand.setSeed(seed);
		WorldUtils.rand.setSeed(seed + 1);
		w.rand.setSeed(seed + 2);
		sct.plants.PlantWorld.rand.setSeed(seed + 3);
		sct.neuro.NeuroBrain.rand.setSeed(seed + 4);
	}
	//
	public static void set(String assignment) {//имя=значение для public static поля Constant
		int eq = assignment.indexOf('=');
		if (eq < 1) {
			fail("ожидалось имя=значение, получено: " + assignment);
		}
		String name = assignment.substring(0, eq).trim();
		String value = assignment.substring(eq + 1).trim();
		if (name.equals("plant_preset")) {//набор растений biosim: config, original, noleak, balanced (до plant.<имя>)
			try {
				sct.plants.PlantConfig.current = sct.plants.PlantConfig.preset(value);
			}catch (IllegalArgumentException e) {
				fail(e.getMessage());
			}
			return;
		}
		if (name.startsWith("neuro.")) {//параметр нейросети популяций по умолчанию: neuro.<имя поля NeuroConfig>
			try {
				sct.neuro.NeuroConfig.current.set(name.substring(6), value);
			}catch (IllegalArgumentException e) {
				fail(e.getMessage());
			}
			return;
		}
		if (name.startsWith("plant.")) {//параметр растений: plant.<имя поля PlantConfig>
			try {
				sct.plants.PlantConfig.current.set(name.substring(6), value);
			}catch (IllegalArgumentException e) {
				fail(e.getMessage());
			}
			return;
		}
		if (name.equals("restart_mode") && !java.util.Set.of("off", "all", "any").contains(value)) {
			fail("restart_mode: off, all или any, получено " + value);
		}
		if (name.equals("elite_fields")) {
			try {
				Elite.check_fields(value);
			}catch (IllegalArgumentException e) {
				fail(e.getMessage());
			}
		}
		try {
			Field f = Constant.class.getField(name);
			if (!Modifier.isStatic(f.getModifiers())) {
				throw new NoSuchFieldException(name);
			}
			Class<?> t = f.getType();
			if (t == int.class) {
				f.setInt(null, Integer.parseInt(value));
			}else if (t == double.class) {
				f.setDouble(null, Double.parseDouble(value));
			}else if (t == String.class) {
				f.set(null, value);
			}else if (t == int[].class) {
				String[] parts = value.split(",");
				int[] arr = new int[parts.length];
				for (int i = 0; i < parts.length; i++) {
					arr[i] = Integer.parseInt(parts[i].trim());
				}
				f.set(null, arr);
			}else {
				fail("параметр " + name + " типа " + t.getSimpleName() + " задать нельзя");
			}
		}catch (NoSuchFieldException e) {
			fail("нет параметра " + name + similar(name));
		}catch (NumberFormatException e) {
			fail("неверное значение " + name + "=" + value);
		}catch (IllegalAccessException e) {
			fail(e.toString());
		}
	}
	//
	private static String similar(String name) {//подсказка при опечатке
		List<String> hits = new ArrayList<String>();
		String low = name.toLowerCase();
		for (Field f : Constant.class.getFields()) {
			String n = f.getName().toLowerCase();
			if (n.contains(low) || low.contains(n) || distance(n, low) <= 3) {
				hits.add(f.getName());
			}
		}
		return hits.isEmpty() ? "" : ", возможно: " + hits;
	}
	private static int distance(String a, String b) {//расстояние Левенштейна
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
	private static String need(String[] args, int i, String key) {
		if (i >= args.length) {
			fail("после " + key + " нужно значение");
		}
		return args[i];
	}
	private static void fail(String msg) {//в аргументах командной строки — выход (apply_args), в диалоге — сообщение
		throw new IllegalArgumentException(msg);
	}
	private static void help() {
		System.out.println("Аргументы: --preset <" + String.join("|", PRESETS.keySet()) + ">, --set имя=значение, --seed N, --w N, --h N, --bots N, --theme dark|light");
		System.out.println("Параметры для --set — public static поля sct.Constant (int, double, int[] через запятую).");
	}
}
