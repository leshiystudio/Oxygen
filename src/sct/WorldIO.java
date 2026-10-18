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
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

//Сохранение и загрузка мира (.oxw — двоичный формат, сжатый gzip).
//В файле: размеры, карты ресурсов, все боты (геномы, связи, королевы) и состояние генераторов
//случайных чисел — загруженный мир продолжается так же, как продолжился бы без сохранения.
//Параметры из Constant не сохраняются: при загрузке должны совпадать размер мира и генома.
public class WorldIO {
	private static final int magic = 0x4F585957;//"OXYW"
	private static final int version = 1;
	//
	public static void save(WorldBio w, File file) throws IOException {
		if (w.plants != null) {
			throw new IOException("мир с растениями пока не сохраняется (формат версии 2 — этап 4)");
		}
		w.lock.lock();
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
			out.writeInt(magic);
			out.writeInt(version);
			out.writeInt(Constant.world_scale[0]);
			out.writeInt(Constant.world_scale[1]);
			out.writeInt(Constant.mind_count);
			out.writeInt(Constant.mind_size);
			out.writeInt(w.steps);
			out.writeInt(w.population);
			write_random(out, Bot.rand);
			write_random(out, WorldUtils.rand);
			write_random(out, w.rand);
			write_map(out, w.oxygen_map);
			write_map(out, w.co2_map);
			write_map(out, w.org_map);
			write_map(out, w.mnr_map);
			for (int[] row : w.height_map) {
				for (int v : row) {
					out.writeInt(v);
				}
			}
			IdentityHashMap<Bot, Integer> ids = new IdentityHashMap<Bot, Integer>();
			ArrayList<Bot> alive = new ArrayList<Bot>();
			for (IBot o : w.objects) {//формат версии 1 хранит ботов Oxygen; разделы других видов — версия 2 (этап 4)
				if (o instanceof Bot b && b.killed == 0) {
					ids.put(b, alive.size());
					alive.add(b);
				}
			}
			out.writeInt(alive.size());
			for (Bot b : alive) {
				b.save(out, ids);
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
			if (ver != version) {
				throw new IOException("версия файла " + ver + ", поддерживается " + version);
			}
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
			for (int i = 0; i < n; i++) {
				Bot b = Bot.load(in, ox, co2, org, map, objects);
				b.brain = Brains.bot_brain(Constant.bot_brain);//мозг в файле версии 1 не хранится
				queen_refs[i] = b.load_queen_ref;
				objects.add(b);
				map[b.xpos][b.ypos] = b;
			}
			for (int i = 0; i < n; i++) {
				((Bot) objects.get(i)).queen_bot = queen_refs[i] >= 0 ? (Bot) objects.get(queen_refs[i]) : null;
			}
			//замена мира целиком — под блокировкой, симуляция в это время стоит
			w.lock.lock();
			try {
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
				Bot.rand = r_bot;
				WorldUtils.rand = r_utils;
				w.rand = r_world;
				Ledger.reset(w);
				History.clear();
				KinClans.update(w);
			}finally {
				w.lock.unlock();
			}
		}catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}
	//
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
