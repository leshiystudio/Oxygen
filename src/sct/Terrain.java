package sct;

//Режим высот (Constant.terrain = 1): горы из карты высот мира (WorldBio.height_map — шум, есть всегда),
//температура по высоте, снос газов по склонам и зависимость фотосинтеза от температуры.
//
//Высота — 0 (самая низкая клетка мира) .. 1 (самая высокая), в метрах — terrain_height_min..max. Температура — по трем точкам:
//temp_low на высоте 0, temp_mid на 0.5, temp_high на 1, между ними линейно.
//Эффективность фотосинтеза — по трем точкам pht_t_min / pht_t_opt / pht_t_max: 0 вне [min, max],
//1 в оптимуме, между ними линейно. Ей умножается энергия photo ботов и солнце листьев растений.
//CO2 тяжелее воздуха — растекается больше вниз, кислород — вверх (gas_drift, WorldUtils.gas).
//Выключено — фотосинтез x1, газы не сносятся: правила мира прежние.
public class Terrain {
	private static int[][] source;//карта высот, по которой посчитано (новый или загруженный мир — новая карта)
	private static String params = "";
	private static double[][] height, meters, temp, eff;

	public static boolean on() {
		return Constant.terrain != 0;
	}
	//пересчитать, если сменилась карта высот или параметры (раз в ход мира, дешево)
	public static void update(WorldBio w) {
		String p = Constant.temp_low + "," + Constant.temp_mid + "," + Constant.temp_high + ","
			+ Constant.pht_t_min + "," + Constant.pht_t_opt + "," + Constant.pht_t_max + ","
			+ Constant.terrain_height_min + "," + Constant.terrain_height_max;
		if (w.height_map == source && p.equals(params)) {
			return;
		}
		source = w.height_map;
		params = p;
		int wx = source.length, hy = source[0].length;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int[] col : source) {
			for (int v : col) {
				lo = Math.min(lo, v);
				hi = Math.max(hi, v);
			}
		}
		double range = Math.max(1, hi - lo);
		height = new double[wx][hy];
		meters = new double[wx][hy];
		temp = new double[wx][hy];
		eff = new double[wx][hy];
		for (int x = 0; x < wx; x++) {
			for (int y = 0; y < hy; y++) {
				double h = (source[x][y] - lo) / range;
				height[x][y] = h;
				meters[x][y] = Constant.terrain_height_min + h * (Constant.terrain_height_max - Constant.terrain_height_min);
				temp[x][y] = h < 0.5 ? lerp(Constant.temp_low, Constant.temp_mid, h / 0.5) : lerp(Constant.temp_mid, Constant.temp_high, (h - 0.5) / 0.5);
				eff[x][y] = efficiency(temp[x][y]);
			}
		}
	}
	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
	public static double efficiency(double t) {//0 вне [min, max], 1 в оптимуме
		double min = Constant.pht_t_min, opt = Constant.pht_t_opt, max = Constant.pht_t_max;
		if (t <= min || t >= max) {
			return 0;
		}
		return t <= opt ? (opt > min ? (t - min) / (opt - min) : 1) : (max > opt ? (max - t) / (max - opt) : 1);
	}
	//множитель фотосинтеза в клетке: 1 — режим выключен
	public static double pht_eff(int x, int y) {
		return on() && eff != null ? eff[x][y] : 1.0;
	}
	public static double height(int x, int y) {//0..1; для отрисовки
		return height != null ? height[x][y] : 0;
	}
	public static double meters(int x, int y) {//высота клетки, м (terrain_height_min..max)
		return meters != null ? meters[x][y] : 0;
	}
	public static double temperature(int x, int y) {
		return temp != null ? temp[x][y] : Constant.temp_mid;
	}
	//высоты в метрах для растекания газов по склону (WorldUtils.gas); null — режим выключен
	public static double[][] heights() {
		return on() ? meters : null;
	}
}
