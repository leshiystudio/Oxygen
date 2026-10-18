package sct;

import java.util.Random;
import java.util.stream.IntStream;

public class WorldUtils {
	public static Random rand = new Random();
	public static void minerals(double[][] mnr_map) {//минералы
		//приход минералов
		int ymin = Constant.world_scale[1] - Constant.world_scale[1] / 8 * 2;
		for (int i = 0; i < 100; i++) {
			int x = rand.nextInt(Constant.world_scale[0]);
			int y = rand.nextInt(ymin, Constant.world_scale[1]);
			mnr_map[x][y] += rand.nextInt(10, 30);
			if (mnr_map[x][y] > 1000) {
				mnr_map[x][y] = 1000;
			}
		}
		//TODO: логика минералов (растекание, потребление). Раньше здесь карта копировалась
		//из пустого new_map, и минералы обнулялись каждый шаг
	}
	//
	//Распространение газа. Каждая клетка, где газа не меньше ox_distribution_min, теряет часть
	//на испарение и делит остаток поровну между собой и проходимыми соседями
	//(проходимый — органики меньше org_die_level). Остальные клетки газ держат.
	//
	//Считается в два прохода «на сбор»: сначала доля каждой клетки (share), затем каждая клетка
	//собирает свою долю и доли соседей. Так клетки пишут только в себя, и строки можно считать
	//параллельно. Результат тот же, что у прежнего «раздающего» обхода.
	//Буферы переиспользуются: раньше на каждый вызов создавалась карта 400x400 и по 16 int[]
	//на клетку — это было 75% времени шага.
	private static double[][] share;
	private static boolean[][] pass;
	private static double[][] next;
	private static int[] wrap_x_minus, wrap_x_plus, wrap_y_minus, wrap_y_plus;
	//
	private static void ensure_buffers(int w, int h) {
		if (share == null || share.length != w || share[0].length != h) {
			share = new double[w][h];
			pass = new boolean[w][h];
			next = new double[w][h];
			wrap_x_minus = new int[w];
			wrap_x_plus = new int[w];
			wrap_y_minus = new int[h];
			wrap_y_plus = new int[h];
			for (int x = 0; x < w; x++) {
				wrap_x_minus[x] = (x - 1 + w) % w;
				wrap_x_plus[x] = (x + 1) % w;
			}
			for (int y = 0; y < h; y++) {
				wrap_y_minus[y] = (y - 1 + h) % h;
				wrap_y_plus[y] = (y + 1) % h;
			}
		}
	}
	//
	public static void gas(double[][] gas_map, double[][] org_map) {//распространение кислорода
		int w = Constant.world_scale[0];
		int h = Constant.world_scale[1];
		ensure_buffers(w, h);
		double min = Constant.ox_distribution_min;
		double evap = Constant.evaporation_ox_coeff;
		double die = Constant.org_die_level;
		//проход 0: проходимость клеток
		IntStream.range(0, w).parallel().forEach(x -> {
			boolean[] p = pass[x];
			double[] o = org_map[x];
			for (int y = 0; y < h; y++) {
				p[y] = o[y] < die;
			}
		});
		//проход 1: доля, которую раздает клетка (или -1, если клетка газ не раздает)
		IntStream.range(0, w).parallel().forEach(x -> {
			int xm = wrap_x_minus[x], xp = wrap_x_plus[x];
			double[] g = gas_map[x];
			double[] sh = share[x];
			for (int y = 0; y < h; y++) {
				if (g[y] >= min) {
					int ym = wrap_y_minus[y], yp = wrap_y_plus[y];
					int count = 1;
					if (pass[x][ym]) count++;
					if (pass[xp][ym]) count++;
					if (pass[xp][y]) count++;
					if (pass[xp][yp]) count++;
					if (pass[x][yp]) count++;
					if (pass[xm][yp]) count++;
					if (pass[xm][y]) count++;
					if (pass[xm][ym]) count++;
					sh[y] = g[y] * evap / count;
				}else {
					sh[y] = -1;
				}
			}
		});
		//проход 2: клетка собирает свою долю и доли раздающих соседей (если сама проходима)
		IntStream.range(0, w).parallel().forEach(x -> {
			int xm = wrap_x_minus[x], xp = wrap_x_plus[x];
			double[] g = gas_map[x];
			double[] n = next[x];
			for (int y = 0; y < h; y++) {
				double v = share[x][y] >= 0 ? share[x][y] : g[y];
				if (pass[x][y]) {
					int ym = wrap_y_minus[y], yp = wrap_y_plus[y];
					v += pos(share[x][ym]) + pos(share[xp][ym]) + pos(share[xp][y]) + pos(share[xp][yp])
						+ pos(share[x][yp]) + pos(share[xm][yp]) + pos(share[xm][y]) + pos(share[xm][ym]);
				}
				n[y] = v > 1 ? 1 : v;
			}
		});
		for (int x = 0; x < w; x++) {
			System.arraycopy(next[x], 0, gas_map[x], 0, h);
		}
	}
	private static double pos(double v) {
		return v > 0 ? v : 0;
	}
}
