package sct;

import java.util.stream.IntStream;

//Рельеф «peaks» — перенос генератора гор из IndustrialGate (Core/Generation/LandformNoise, CpuReliefFields):
//равнины с холмами + маска гор + острые изогнутые гребни (ridged multifractal с ручками остроты и детали,
//по мотивам Sebastian Lague, Solar System, MIT), без эрозии.
//
//Мир Oxygen замкнут, поэтому шум периодический: решетка заворачивается на свой период, а все частоты —
//целое число клеток решетки на мир. Гребни остаются острыми и через край, без смешивания копий.
//Масштаб — terrain_size: сколько «волн» гор укладывается по ширине мира.
public class Relief {
	static final double PEAK_GAIN = 1.3, PEAK_WARP = 0.35, PEAK_SMOOTHING = 0.02;
	static final int PEAK_OCTAVES = 6;

	//высоты 0..1000 (как WorldBio.height_map): самая низкая клетка — 0, самая высокая — 1000
	public static int[][] generate(int w, int h, int seed) {
		int s = Math.max(1, Constant.terrain_size);//волн по ширине
		int sy = Math.max(1, (int) Math.round(s * (double) h / w));//по высоте — с сохранением пропорций
		double power = Constant.terrain_sharpness;
		octaves = octaves_for(Math.min(w, h), Math.max(s, sy));
		double[][] raw = new double[w][h];
		double[][] hills = new double[w][h];
		double[][] peaks = new double[w][h];
		double[][] detail = new double[w][h];
		IntStream.range(0, w).parallel().forEach(x -> {
			for (int y = 0; y < h; y++) {
				double u = (double) x / w, v = (double) y / h;
				raw[x][y] = fractal(u, v, s, sy, 0, 0, seed, 31, 4);//где встают горы
				hills[x][y] = fractal(u, v, 2 * s + 1, 2 * sy + 1, 3.3, 9.1, seed, 71, 4) * 0.5 + 0.5;
				detail[x][y] = fractal(u, v, 9 * s, 9 * sy, 1.7, 6.2, seed, 137, 3);
				peaks[x][y] = peaks(u, v, s, sy, seed, power);
			}
		});
		//маска гор: доля гор terrain_mountains — по квантилю, с мягким краем (как в IndustrialGate)
		double[] all = new double[w * h];
		for (int x = 0, k = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				all[k++] = raw[x][y];
			}
		}
		java.util.Arrays.sort(all);
		double share = Math.max(0, Math.min(1, Constant.terrain_mountains));
		double cut = all[Math.max(0, Math.min(all.length - 1, (int) ((1 - share) * (all.length - 1))))];
		double[][] height = new double[w][h];
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				double mountain = share <= 0 ? 0 : smoothstep(cut - 0.3, cut + 0.3, raw[x][y]);
				double plain = 0.06 + 0.12 * hills[x][y] + 0.015 * detail[x][y];
				double range = mountain * (0.1 + 0.9 * peaks[x][y]) * (0.75 + 0.25 * hills[x][y]);
				height[x][y] = plain + range;
				lo = Math.min(lo, height[x][y]);
				hi = Math.max(hi, height[x][y]);
			}
		}
		int[][] out = new int[w][h];
		double span = Math.max(1e-12, hi - lo);
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				out[x][y] = (int) Math.round((height[x][y] - lo) / span * 1000);
			}
		}
		return out;
	}
	//
	//ШУМ: значения зависят только от аргументов. Решетка шириной nx x ny клеток на мир, заворачивается.
	//
	static double hash(int x, int z, int seed, int salt) {//0..1
		int value = seed ^ x * 0x9E3779B9 ^ z * 0x85EBCA6B ^ salt * 0xC2B2AE35;
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		value ^= value >>> 16;
		return (value & 0x00FFFFFF) / 16777216.0;
	}
	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
	private static double corner(int ix, int iz, double dx, double dz, int seed, int salt) {
		double angle = hash(ix, iz, seed, salt) * Math.PI * 2;
		return Math.cos(angle) * dx + Math.sin(angle) * dz;
	}
	//градиентный шум около -1..1; x, z — в клетках решетки, период px, pz клеток
	static double gradient(double x, double z, int px, int pz, int seed, int salt) {
		int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
		double fx = x - ix, fz = z - iz, u = fade(fx), v = fade(fz);
		int x0 = Math.floorMod(ix, px), x1 = Math.floorMod(ix + 1, px);
		int z0 = Math.floorMod(iz, pz), z1 = Math.floorMod(iz + 1, pz);
		double a = corner(x0, z0, fx, fz, seed, salt), b = corner(x1, z0, fx - 1, fz, seed, salt);
		double c = corner(x0, z1, fx, fz - 1, seed, salt), d = corner(x1, z1, fx - 1, fz - 1, seed, salt);
		double top = a + (b - a) * u, bottom = c + (d - c) * u;
		return (top + (bottom - top) * v) * 1.41421356;
	}
	//фрактальная сумма октав около -1..1; u, v — доли мира 0..1, n — клеток решетки на мир; ox, oz — сдвиг в клетках
	static double fractal(double u, double v, int nx, int nz, double ox, double oz, int seed, int salt, int octaves) {
		double sum = 0, amplitude = 1, total = 0;
		int f = 1;
		for (int i = 0; i < octaves; i++) {
			sum += gradient((u * nx + ox) * f, (v * nz + oz) * f, nx * f, nz * f, seed, salt + i * 101) * amplitude;
			total += amplitude;
			amplitude *= 0.5;
			f *= 2;
		}
		return sum / total;
	}
	//гребневый шум 0..1: power — острота гребня, gain — сколько детали мелкие октавы кладут на гребень
	static double ridged_power(double x, double z, int nx, int nz, int seed, int salt, int octaves, double power, double gain) {
		double sum = 0, amplitude = 0.5, total = 0, weight = 1;
		int f = 1;
		for (int i = 0; i < octaves; i++) {
			double n = Math.pow(1 - Math.abs(gradient(x * f, z * f, nx * f, nz * f, seed, salt + i * 131)), power) * weight;
			weight = Math.max(0, Math.min(1, n * gain));
			sum += n * amplitude;
			total += amplitude;
			amplitude *= 0.5;
			f *= 2;
		}
		return sum / total;
	}
	//пики: изогнутые (warp) острые гребни, усредненные по 5 близким точкам — острые на масштабе хребта,
	//но без зубцов от клетки к клетке. Около 0..1.3
	static double peaks(double u, double v, int nx, int nz, int seed, double power) {
		int wx = Math.max(1, nx / 2), wz = Math.max(1, nz / 2);
		double wu = fractal(u, v, wx, wz, 3.1, 7.7, seed, 241, 3) * PEAK_WARP;
		double wv = fractal(u, v, wx, wz, 9.4, 1.2, seed, 251, 3) * PEAK_WARP;
		double pu = u * nx + wu + 12.5, pv = v * nz + wv + 4.4, o = PEAK_SMOOTHING;
		double ridge = (sample(pu, pv, nx, nz, seed, power) + sample(pu - o, pv, nx, nz, seed, power)
			+ sample(pu + o, pv, nx, nz, seed, power) + sample(pu, pv - o, nx, nz, seed, power)
			+ sample(pu, pv + o, nx, nz, seed, power)) / 5;
		return 0.08 + 1.25 * ridge;
	}
	private static double sample(double u, double v, int nx, int nz, int seed, double power) {
		return ridged_power(u, v, nx, nz, seed, 263, octaves, power, PEAK_GAIN);
	}
	//октав гребней — не больше PEAK_OCTAVES и так, чтобы самая мелкая деталь была не меньше ~3 клеток:
	//на маленьком мире мелкие октавы ложатся на 1-2 клетки и превращают хребты в рябь
	private static int octaves = PEAK_OCTAVES;
	private static int octaves_for(int cells, int waves) {
		int n = 1;
		while (n < PEAK_OCTAVES && (double) cells / (waves * (1 << n)) >= 3) {
			n++;
		}
		return n;
	}
	static double smoothstep(double e0, double e1, double x) {
		double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}
}
