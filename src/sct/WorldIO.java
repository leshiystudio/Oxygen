package sct;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import sct.plants.Plant;
import sct.plants.PlantWorld;

//Сохранение и загрузка мира (.oxw — двоичный формат, сжатый gzip).
//
//Версия 3 (сохраняется сейчас) = версия 2 + веса и память нейросети каждого существа.
//Версия 2: настройки мира текстом (популяции, взаимодействия, параметры Constant
//и растений — как в диалоге «Новый мир»), карты, все существа в порядке списка мира (вид, популяция,
//поля) и состояние генераторов случайных чисел — загруженный мир продолжается так же, как
//продолжился бы без сохранения. Параметры при загрузке берутся из файла.
//Версия 1 (раньше): только боты Oxygen, параметры — текущие; загружается в первую популяцию ботов.
public class WorldIO {
	private static final int magic = 0x4F585957;//"OXYW"
	private static final int version = 3;
	private static final int KIND_BOT = 0, KIND_PLANT = 1;
	//
	public static void save(WorldBio w, File file) throws IOException {
		w.lock.lock();
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
			out.writeInt(magic);
			out.writeInt(version);
			WorldSettings ws = WorldSettings.capture();//параметры, с которыми мир живет сейчас
			ws.populations.clear();
			for (Population p : w.populations) {
				ws.populations.add(WorldSettings.copy(p));
			}
			ws.interaction = WorldSettings.copy(Interaction.current, w.populations.size());
			byte[] text = ws.to_text().getBytes(java.nio.charset.StandardCharsets.UTF_8);//не writeUTF: у него предел 64 КБ
			out.writeInt(text.length);
			out.write(text);
			out.writeInt(Constant.world_scale[0]);
			out.writeInt(Constant.world_scale[1]);
			out.writeInt(w.steps);
			out.writeInt(w.population);
			write_random(out, Bot.rand);
			write_random(out, WorldUtils.rand);
			write_random(out, w.rand);
			write_random(out, PlantWorld.rand);
			write_random(out, sct.neuro.NeuroBrain.rand);
			write_map(out, w.oxygen_map);
			write_map(out, w.co2_map);
			write_map(out, w.org_map);
			write_map(out, w.mnr_map);
			for (int[] row : w.height_map) {
				for (int v : row) {
					out.writeInt(v);
				}
			}
			IdentityHashMap<IBot, Integer> ids = new IdentityHashMap<IBot, Integer>();
			List<IBot> alive = new ArrayList<IBot>();
			for (IBot o : w.objects) {//порядок списка — порядок ходов: сохраняется как есть
				if (o.alive()) {
					ids.put(o, alive.size());
					alive.add(o);
				}
			}
			out.writeInt(alive.size());
			for (IBot o : alive) {
				out.writeInt(o instanceof Plant ? KIND_PLANT : KIND_BOT);
				out.writeInt(o.population() == null ? -1 : o.population().id);
				if (o instanceof Plant p) {
					p.save(out);
					write_brain(out, p.brain);
				}else {
					((Bot) o).save(out, ids);
					write_brain(out, ((Bot) o).brain);
				}
			}
		}finally {
			w.lock.unlock();
		}
	}
	//
	public static void load(WorldBio w, File file) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
			if (in.readInt() != magic) {
				throw new IOException("не файл мира Oxygen");
			}
			int ver = in.readInt();
			if (ver == 1) {
				load_v1(w, in);
			}else if (ver == 2 || ver == 3) {
				load_v2(w, in, ver == 3);
			}else {
				throw new IOException("версия файла " + ver + ", поддерживаются 1 и 2");
			}
		}catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}
	//
	private static void load_v2(WorldBio w, DataInputStream in, boolean brains) throws IOException, ClassNotFoundException {
		byte[] text = new byte[in.readInt()];
		in.readFully(text);
		WorldSettings ws = WorldSettings.from_text(new String(text, java.nio.charset.StandardCharsets.UTF_8));
		w.lock.lock();//параметры, карты и существа меняются вместе — симуляция в это время стоит
		try {
			for (Map.Entry<String, String> e : ws.constants.entrySet()) {
				Presets.set(e.getKey() + "=" + e.getValue());
			}
			int ww = in.readInt(), wh = in.readInt();
			if (ww != Constant.world_scale[0] || wh != Constant.world_scale[1]) {
				throw new IOException("размер мира в файле не совпадает с его параметрами");
			}
			int steps = in.readInt();
			int population = in.readInt();
			Random r_bot = read_random(in), r_utils = read_random(in), r_world = read_random(in), r_plant = read_random(in);
			Random r_neuro = brains ? read_random(in) : null;
			double[][] ox = read_map(in, ww, wh), co2 = read_map(in, ww, wh), org = read_map(in, ww, wh), mnr = read_map(in, ww, wh);
			int[][] height = new int[ww][wh];
			for (int x = 0; x < ww; x++) {
				for (int y = 0; y < wh; y++) {
					height[x][y] = in.readInt();
				}
			}
			IBot[][] map = new IBot[ww][wh];
			ArrayList<IBot> objects = new ArrayList<IBot>();
			List<Population> pops = ws.populations;
			PlantWorld first_plants = null;
			for (int i = 0; i < pops.size(); i++) {//популяции оживают: мозги и данные растений
				Population p = pops.get(i);
				p.id = i;
				if (p.species == Species.PLANT) {
					p.plant_world = new PlantWorld(map, org, mnr, objects, p.plant_config);
					p.plant_world.pop = p;
					p.plant_brain = Brains.make_plant_brain(p);
					if (first_plants == null) {
						first_plants = p.plant_world;
					}
				}else {
					p.bot_brain = Brains.make_bot_brain(p);
				}
			}
			int n = in.readInt();
			int[] queen_refs = new int[n];
			java.util.Arrays.fill(queen_refs, -1);
			for (int i = 0; i < n; i++) {
				int kind = in.readInt();
				int pid = in.readInt();
				Population p = pid >= 0 ? pops.get(pid) : null;
				IBot o;
				if (kind == KIND_PLANT) {
					Plant plant = Plant.load(in, p.plant_world, p.plant_brain);
					if (brains) {
						plant.brain = read_brain(in, p, sct.neuro.PlantBody.instance, p.plant_brain);
					}
					o = plant;
				}else {
					Bot b = Bot.load(in, ox, co2, org, map, objects);
					b.pop = p;
					b.brain = p != null ? p.bot_brain : null;
					if (brains) {
						b.brain = read_brain(in, p, sct.neuro.BotBody.instance, b.brain);
					}
					queen_refs[i] = b.load_queen_ref;
					o = b;
				}
				objects.add(o);
				map[o.cell_x()][o.cell_y()] = o;
			}
			for (int i = 0; i < n; i++) {
				if (queen_refs[i] >= 0) {
					((Bot) objects.get(i)).queen_bot = (Bot) objects.get(queen_refs[i]);
				}
			}
			w.populations = pops;
			w.next_populations = null;
			w.next_interaction = null;
			Interaction.current = ws.interaction;
			History.name_populations(pops);
			w.plants = first_plants;
			install(w, objects, map, ox, co2, org, mnr, height, steps, population);
			Bot.rand = r_bot;
			WorldUtils.rand = r_utils;
			w.rand = r_world;
			PlantWorld.rand = r_plant;
			if (r_neuro != null) {
				sct.neuro.NeuroBrain.rand = r_neuro;
			}
			Ledger.reset(w);
			History.clear();
			KinClans.update(w);
		}finally {
			w.lock.unlock();
		}
	}
	//
	private static void load_v1(WorldBio w, DataInputStream in) throws IOException, ClassNotFoundException {
		int ww = in.readInt(), wh = in.readInt(), mc = in.readInt(), ms = in.readInt();
		if (ww != Constant.world_scale[0] || wh != Constant.world_scale[1] || mc != Constant.mind_count || ms != Constant.mind_size) {
			throw new IOException("мир " + ww + "x" + wh + ", геном " + mc + "x" + ms + "; сейчас мир "
				+ Constant.world_scale[0] + "x" + Constant.world_scale[1] + ", геном " + Constant.mind_count + "x" + Constant.mind_size
				+ " — запустите с теми же --w/--h/--set");
		}
		int steps = in.readInt();
		int population = in.readInt();
		Random r_bot = read_random(in), r_utils = read_random(in), r_world = read_random(in);
		double[][] ox = read_map(in, ww, wh), co2 = read_map(in, ww, wh), org = read_map(in, ww, wh), mnr = read_map(in, ww, wh);
		int[][] height = new int[ww][wh];
		for (int x = 0; x < ww; x++) {
			for (int y = 0; y < wh; y++) {
				height[x][y] = in.readInt();
			}
		}
		IBot[][] map = new IBot[ww][wh];
		ArrayList<IBot> objects = new ArrayList<IBot>();
		int n = in.readInt();
		int[] queen_refs = new int[n];
		Population bots_pop = null;//формат версии 1 — один вид: боты попадают в первую популяцию ботов мира
		for (Population p : w.populations) {
			if (p.species == Species.OXYGEN_BOT) {
				bots_pop = p;
				break;
			}
		}
		for (int i = 0; i < n; i++) {
			Bot b = Bot.load(in, ox, co2, org, map, objects);
			b.pop = bots_pop;
			b.brain = bots_pop != null ? bots_pop.bot_brain : Brains.bot_brain(Constant.bot_brain);//мозг в файле версии 1 не хранится
			queen_refs[i] = b.load_queen_ref;
			objects.add(b);
			map[b.xpos][b.ypos] = b;
		}
		for (int i = 0; i < n; i++) {
			((Bot) objects.get(i)).queen_bot = queen_refs[i] >= 0 ? (Bot) objects.get(queen_refs[i]) : null;
		}
		w.lock.lock();//замена мира целиком — под блокировкой, симуляция в это время стоит
		try {
			w.plants = null;
			install(w, objects, map, ox, co2, org, mnr, height, steps, population);
			Bot.rand = r_bot;
			WorldUtils.rand = r_utils;
			w.rand = r_world;
			Ledger.reset(w);
			History.clear();
			KinClans.update(w);
		}finally {
			w.lock.unlock();
		}
	}
	private static void install(WorldBio w, ArrayList<IBot> objects, IBot[][] map, double[][] ox, double[][] co2, double[][] org,
			double[][] mnr, int[][] height, int steps, int population) {
		w.objects = objects;
		w.Map = map;
		w.oxygen_map = ox;
		w.co2_map = co2;
		w.org_map = org;
		w.mnr_map = mnr;
		w.height_map = height;
		w.steps = steps;
		w.population = population;
		w.selection = null;
		w.prev_selection = null;
	}
	//
	private static void write_brain(DataOutputStream out, Brain<?> brain) throws IOException {//1 — нейросеть (веса и память), 0 — нет
		if (brain instanceof sct.neuro.NeuroBrain<?> nb) {
			out.writeInt(1);
			nb.write(out);
		}else {
			out.writeInt(0);
		}
	}
	private static <B extends IBot> Brain<B> read_brain(DataInputStream in, Population p, sct.neuro.NeuroBody<B> body, Brain<B> fallback) throws IOException {
		if (in.readInt() == 0) {
			return fallback;
		}
		return sct.neuro.NeuroBrain.read(in, p.neuro_config(), body);
	}
	private static void write_map(DataOutputStream out, double[][] m) throws IOException {
		for (double[] row : m) {
			for (double v : row) {
				out.writeDouble(v);
			}
		}
	}
	private static double[][] read_map(DataInputStream in, int w, int h) throws IOException {
		double[][] m = new double[w][h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				m[x][y] = in.readDouble();
			}
		}
		return m;
	}
	private static void write_random(DataOutputStream out, Random r) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream o = new ObjectOutputStream(bytes)) {
			o.writeObject(r);
		}
		out.writeInt(bytes.size());
		out.write(bytes.toByteArray());
	}
	private static Random read_random(DataInputStream in) throws IOException, ClassNotFoundException {
		byte[] b = new byte[in.readInt()];
		in.readFully(b);
		try (ObjectInputStream o = new ObjectInputStream(new ByteArrayInputStream(b))) {
			return (Random) o.readObject();
		}
	}
}
