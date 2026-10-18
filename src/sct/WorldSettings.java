package sct;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import sct.plants.PlantAction;
import sct.plants.PlantConfig;

//Настройки нового мира целиком: параметры Constant, зерно, популяции (с их настройками и
//включенными действиями), таблица взаимодействий. Диалог «Новый мир» редактирует снимок,
//сохраняет и загружает его (.properties), «Создать» применяет под блокировкой мира.
public class WorldSettings {
	public Map<String, String> constants = new LinkedHashMap<String, String>();//имя поля Constant -> значение
	public long seed = -1;
	public List<Population> populations = new ArrayList<Population>();
	public Interaction interaction;
	//
	public static WorldSettings startup;//настройки при запуске программы (с аргументами) — кнопка «По умолчанию»

	//
	//снимок текущих параметров
	public static WorldSettings capture() {
		WorldSettings s = new WorldSettings();
		for (Field f : constant_fields()) {
			s.constants.put(f.getName(), get_constant(f));
		}
		s.seed = Presets.seed;
		for (Population p : Population.defaults()) {
			s.populations.add(p);
		}
		s.interaction = new Interaction(s.populations.size());
		return s;
	}
	public WorldSettings copy() {
		WorldSettings s = new WorldSettings();
		s.constants.putAll(constants);
		s.seed = seed;
		for (Population p : populations) {
			s.populations.add(copy(p));
		}
		s.interaction = copy(interaction, populations.size());
		return s;
	}
	//
	//применить к миру и создать его (под блокировкой — поток симуляции не увидит параметры наполовину)
	public void apply(WorldBio w) {
		w.lock.lock();
		try {
			for (Map.Entry<String, String> e : constants.entrySet()) {
				Presets.set(e.getKey() + "=" + e.getValue());
			}
			Presets.seed = seed;
			Presets.apply_seed(w);
			List<Population> pops = new ArrayList<Population>();
			for (Population p : populations) {
				pops.add(copy(p));
			}
			w.next_populations = pops;
			w.next_interaction = copy(interaction, pops.size());
			w.newPopulation();
		}finally {
			w.lock.unlock();
		}
	}
	//
	//СОХРАНЕНИЕ (.properties: текстом, можно править руками)
	//
	public void save(File file) throws IOException {
		try (FileWriter out = new FileWriter(file, StandardCharsets.UTF_8)) {
			out.write(to_text());
		}
	}
	public static WorldSettings load(File file) throws IOException {
		try (FileReader in = new FileReader(file, StandardCharsets.UTF_8)) {
			return from_reader(in);
		}
	}
	public String to_text() throws IOException {//текст .properties — и для файла настроек, и внутри файла мира
		Properties pr = new Properties() {//порядок ключей в файле — как добавлены
			private static final long serialVersionUID = 1L;
			private final List<Object> order = new ArrayList<Object>();
			public synchronized Object put(Object k, Object v) {
				order.remove(k);
				order.add(k);
				return super.put(k, v);
			}
			public synchronized java.util.Enumeration<Object> keys() {
				return java.util.Collections.enumeration(order);
			}
			public java.util.Set<Map.Entry<Object, Object>> entrySet() {
				java.util.Set<Map.Entry<Object, Object>> set = new java.util.LinkedHashSet<Map.Entry<Object, Object>>();
				for (Object k : order) {
					set.add(new java.util.AbstractMap.SimpleEntry<Object, Object>(k, get(k)));
				}
				return set;
			}
		};
		pr.put("seed", String.valueOf(seed));
		for (Map.Entry<String, String> e : constants.entrySet()) {
			pr.put("constant." + e.getKey(), e.getValue());
		}
		pr.put("populations", String.valueOf(populations.size()));
		for (int i = 0; i < populations.size(); i++) {
			Population p = populations.get(i);
			String k = "population." + i + ".";
			pr.put(k + "name", p.name);
			pr.put(k + "species", p.species.name());
			pr.put(k + "brain", p.brain);
			pr.put(k + "count", String.valueOf(p.count));
			pr.put(k + "color", String.format("#%06X", p.color.getRGB() & 0xFFFFFF));
			pr.put(k + "actions_off", actions_off(p));
			if (p.plant_config != null) {
				for (Field f : PlantConfig.class.getFields()) {
					if (!Modifier.isStatic(f.getModifiers())) {
						pr.put(k + "plant." + f.getName(), field_value(f, p.plant_config));
					}
				}
			}
		}
		for (int a = 0; a < populations.size(); a++) {
			for (int b = 0; b < populations.size(); b++) {
				if (a == b) {
					continue;
				}
				String k = "interaction." + a + "." + b + ".";
				pr.put(k + "eat", String.valueOf(interaction.eat[a][b]));
				pr.put(k + "exchange", String.valueOf(interaction.exchange[a][b]));
				pr.put(k + "share", String.valueOf(interaction.share[a][b]));
				pr.put(k + "chain", String.valueOf(interaction.chain[a][b]));
				pr.put(k + "seed_kills", String.valueOf(interaction.seed_kills[a][b]));
			}
		}
		java.io.StringWriter out = new java.io.StringWriter();
		pr.store(out, "Oxygen: настройки нового мира");
		return out.toString();
	}
	public static WorldSettings from_text(String text) throws IOException {
		return from_reader(new java.io.StringReader(text));
	}
	private static WorldSettings from_reader(java.io.Reader in) throws IOException {
		Properties pr = new Properties();
		pr.load(in);
		WorldSettings s = new WorldSettings();
		try {
			s.seed = Long.parseLong(pr.getProperty("seed", "-1"));
			for (String key : pr.stringPropertyNames()) {
				if (key.startsWith("constant.")) {
					s.constants.put(key.substring(9), pr.getProperty(key));
				}
			}
			int n = Integer.parseInt(pr.getProperty("populations", "0"));
			for (int i = 0; i < n; i++) {
				String k = "population." + i + ".";
				Population p = new Population(pr.getProperty(k + "name"), Species.valueOf(pr.getProperty(k + "species")),
					pr.getProperty(k + "brain"), Integer.parseInt(pr.getProperty(k + "count")), Color.decode(pr.getProperty(k + "color")));
				p.id = i;
				p.actions_off(pr.getProperty(k + "actions_off", ""));
				if (p.plant_config != null) {
					for (String key : pr.stringPropertyNames()) {
						if (key.startsWith(k + "plant.")) {
							p.plant_config.set(key.substring((k + "plant.").length()), pr.getProperty(key));
						}
					}
				}
				s.populations.add(p);
			}
			s.interaction = new Interaction(n);
			for (int a = 0; a < n; a++) {
				for (int b = 0; b < n; b++) {
					String k = "interaction." + a + "." + b + ".";
					if (pr.getProperty(k + "eat") == null) {
						continue;
					}
					s.interaction.eat[a][b] = Double.parseDouble(pr.getProperty(k + "eat"));
					s.interaction.exchange[a][b] = Double.parseDouble(pr.getProperty(k + "exchange"));
					s.interaction.share[a][b] = Boolean.parseBoolean(pr.getProperty(k + "share"));
					s.interaction.chain[a][b] = Boolean.parseBoolean(pr.getProperty(k + "chain"));
					s.interaction.seed_kills[a][b] = Boolean.parseBoolean(pr.getProperty(k + "seed_kills"));
				}
			}
		}catch (RuntimeException e) {
			throw new IOException("файл настроек поврежден: " + e.getMessage(), e);
		}
		return s;
	}
	//
	//ВСПОМОГАТЕЛЬНОЕ
	//
	public static List<Field> constant_fields() {//редактируемые параметры Constant
		List<Field> list = new ArrayList<Field>();
		for (Field f : Constant.class.getFields()) {
			int m = f.getModifiers();
			Class<?> t = f.getType();
			if (Modifier.isStatic(m) && !Modifier.isFinal(m) && (t == int.class || t == double.class || t == String.class || t == int[].class)) {
				list.add(f);
			}
		}
		return list;
	}
	private static String get_constant(Field f) {
		try {
			Object v = f.get(null);
			if (v instanceof int[]) {
				int[] a = (int[]) v;
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < a.length; i++) {
					sb.append(i == 0 ? "" : ",").append(a[i]);
				}
				return sb.toString();
			}
			return String.valueOf(v);
		}catch (IllegalAccessException e) {
			return "";
		}
	}
	public static String field_value(Field f, Object owner) {
		try {
			return String.valueOf(f.get(owner));
		}catch (IllegalAccessException e) {
			return "";
		}
	}
	public static String actions_off(Population p) {
		StringBuilder sb = new StringBuilder();
		Enum<?>[] all = p.species == Species.PLANT ? PlantAction.values() : BotAction.values();
		for (int i = 0; i < p.actions_on.length; i++) {
			if (!p.actions_on[i]) {
				sb.append(sb.length() == 0 ? "" : ",").append(((Action<?>) all[i]).id());
			}
		}
		return sb.toString();
	}
	public static Population copy(Population p) {
		Population c = new Population(p.name, p.species, p.brain, p.count, p.color);
		c.id = p.id;
		c.actions_on = p.actions_on.clone();
		if (p.plant_config != null) {
			c.plant_config = new PlantConfig();
			for (Field f : PlantConfig.class.getFields()) {
				if (!Modifier.isStatic(f.getModifiers())) {
					c.plant_config.set(f.getName(), field_value(f, p.plant_config));
				}
			}
		}
		return c;
	}
	public static Interaction copy(Interaction ia, int n) {
		Interaction c = new Interaction(n);
		if (ia == null) {
			return c;
		}
		int m = Math.min(n, ia.eat.length);
		for (int a = 0; a < m; a++) {
			for (int b = 0; b < m; b++) {
				c.eat[a][b] = ia.eat[a][b];
				c.exchange[a][b] = ia.exchange[a][b];
				c.share[a][b] = ia.share[a][b];
				c.chain[a][b] = ia.chain[a][b];
				c.seed_kills[a][b] = ia.seed_kills[a][b];
			}
		}
		return c;
	}
}
