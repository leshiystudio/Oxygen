package sct;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

//Отрисовка тайла мира прямо в массив пикселей: без fillRect, new Color и BasicStroke на каждую клетку.
//Масштаб тот же, что раньше: клетка — Constant.size x Constant.size пикселей.
//Направление бота — пиксели от центра клетки к ее краю, связи цепочки в режиме королев — так же.
public class PixelRenderer {
	private BufferedImage tile;
	private int[] px;
	private int tw, th;
	//
	private static final int queen_dir = Theme.rgb(50, 50, 50);
	private static final int chain_color = Theme.rgb(255, 150, 0);
	//
	public BufferedImage render(WorldBio w) {
		return render(w, w.draw_type, w.ground_layer, w.gas_layer, Constant.size, w.render, true);
	}
	//фон одним номером (веб-просмотр, Constant.res_type_names) -> слои «земля» и «газ»
	private static final int[][] LEGACY_LAYERS = {{0, 0}, {0, 1}, {1, 1}, {2, 0}, {0, 2}, {3, 0}, {4, 0}};
	public BufferedImage render(WorldBio w, int draw_type, int res_draw_type, int s, boolean bots, boolean overlays) {
		int[] l = LEGACY_LAYERS[Math.max(0, Math.min(LEGACY_LAYERS.length - 1, res_draw_type))];
		return render(w, draw_type, l[0], l[1], s, bots, overlays);
	}
	//кадр с заданными режимами и размером клетки s — веб-просмотр рисует свой кадр, не трогая режимы окна.
	//ground — слой земли (Constant.ground_layer_names), gas — слой газа поверх (Constant.gas_layer_names).
	//overlays — королевы и выделение: они рисуются в пикселях Constant.size, поэтому только при s == Constant.size
	public BufferedImage render(WorldBio w, int draw_type, int ground, int gas, int s, boolean bots, boolean overlays) {
		int ww = Constant.world_scale[0];
		int wh = Constant.world_scale[1];
		if (tile == null || tw != ww * s || th != wh * s) {//тайл создается один раз, а не на каждый кадр
			tw = ww * s;
			th = wh * s;
			tile = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
			px = ((DataBufferInt) tile.getRaster().getDataBuffer()).getData();
		}
		draw_background(w, ground, gas, ww, wh, s);
		if (bots) {
			draw_bots(w, draw_type, s);
		}
		if (bots && overlays && s == Constant.size) {
			Graphics2D g = tile.createGraphics();
			if (draw_type == 7) {//связи королев с подчиненными и сами королевы поверх
				for (IBot o : w.objects) {
					if (o instanceof Bot b && b.queen == 1 && b.killed == 0) {
						b.queen_draw(g);
					}
				}
				for (IBot o : w.objects) {
					if (o instanceof Bot b && b.queen == 1 && b.killed == 0) {
						b.queen_mark(g);
					}
				}
			}
			IBot sel = w.selection;
			if (sel != null) {//выбранное существо
				g.setColor(Color.RED);
				g.setStroke(new BasicStroke(2));
				g.drawRect(sel.cell_x() * s - 2, sel.cell_y() * s - 2, s + 4, s + 4);
			}
			IBot prev = w.prev_selection;
			if (prev != null && prev.alive()) {//предыдущее выбранное — для сравнения геномов
				g.setColor(Color.YELLOW);
				g.setStroke(new BasicStroke(1));
				g.drawRect(prev.cell_x() * s - 2, prev.cell_y() * s - 2, s + 4, s + 4);
			}
			g.dispose();
		}
		return tile;
	}
	//
	private void fill_cell(int x, int y, int s, int color) {
		int start = y * s * tw + x * s;
		for (int r = 0; r < s; r++) {
			Arrays.fill(px, start, start + s, color);
			start += tw;
		}
	}
	//
	//фон двумя слоями: земля (0 нет, 1 органика, 2 минералы, 3 высота, 4 тепло) и газ поверх (0 нет, 1 кислород, 2 CO2)
	private void draw_background(WorldBio w, int ground, int gas, int ww, int wh, int s) {
		if (ground == 0 && gas == 0) {
			Arrays.fill(px, Theme.world_bg);
			return;
		}
		double ox_max = Constant.ox_render_maximum_coeff;
		double co2_max = Constant.co2_render_maximum_coeff;
		double die = Constant.org_die_level;
		double t_lo = Math.min(Constant.temp_low, Math.min(Constant.temp_mid, Constant.temp_high));
		double t_span = Math.max(1e-9, Math.max(Constant.temp_low, Math.max(Constant.temp_mid, Constant.temp_high)) - t_lo);
		double soil_scale = w.plants != null ? w.plants.c.max_energy_around : 1000.0;
		double gas_alpha = ground == 0 ? 1.0 : 210 / 255.0;//поверх земли газ полупрозрачный — земля видна
		for (int x = 0; x < ww; x++) {
			for (int y = 0; y < wh; y++) {
				int c = Theme.world_bg;
				double org = w.org_map[x][y];
				if (ground == 1) {//органика
					c = Theme.mix(Theme.world_bg, Theme.organics, org / die);
				}else if (ground == 2) {//минералы (у растений — почва): корневая шкала до порога токсичности,
					//иначе стартовые 100 из 1500 на темном фоне почти не видны
					c = Theme.mix(Theme.world_bg, Theme.minerals, Math.sqrt(Math.max(0, w.mnr_map[x][y]) / soil_scale));
				}else if (ground == 3) {//высоты: 0 — самая низкая клетка мира, 1 — самая высокая (Terrain)
					c = Theme.mix(Theme.world_bg, 0xE8E0D0, Terrain.height(x, y));
				}else if (ground == 4) {//тепло: синий — холодно, желтый — середина, красный — жарко (Terrain, temp_low/mid/high)
					double t = (Terrain.temperature(x, y) - t_lo) / t_span;
					c = t < 0.5 ? Theme.mix(0x3060D0, 0xE8D060, t * 2) : Theme.mix(0xE8D060, 0xD03020, (t - 0.5) * 2);
				}
				if (gas == 1) {
					c = Theme.mix(c, Theme.oxygen, w.oxygen_map[x][y] / ox_max * gas_alpha);
				}else if (gas == 2) {
					c = Theme.mix(c, Theme.co2, w.co2_map[x][y] / co2_max * gas_alpha);
				}
				if ((gas != 0 || ground == 1) && org >= die) {//мертвая зона: газы через нее не проходят
					c = Theme.dead_zone;
				}
				fill_cell(x, y, s, c);
			}
		}
	}
	//
	private void draw_bots(WorldBio w, int dt, int s) {
		int half = s / 2;
		int len = (s - 1) / 2;//от центра до края клетки: при четном s длина half вышла бы в соседнюю клетку (и за край тайла)
		for (IBot o : w.objects) {
			if (!o.alive()) {
				continue;
			}
			if (!(o instanceof Bot b)) {//другие виды — клеткой своего цвета
				fill_cell(o.cell_x(), o.cell_y(), s, o.color(dt));
				continue;
			}
			int c = b.get_color(dt).getRGB() & 0xFFFFFF;
			int x0 = b.xpos * s;
			int y0 = b.ypos * s;
			if (b.state == 1) {//семечко — маленький квадрат в центре
				int q = s / 4;
				for (int yy = y0 + q; yy < y0 + s - q; yy++) {
					Arrays.fill(px, yy * tw + x0 + q, yy * tw + x0 + s - q, c);
				}
			}else {
				fill_cell(b.xpos, b.ypos, s, c);
			}
			int cx = x0 + half;
			int cy = y0 + half;
			int dir_color = dt == 7 ? queen_dir : Theme.direction;
			line_from_center(cx, cy, b.rotate, len, dir_color);
			if (dt == 7) {
				for (int i = 0; i < 8; i++) {
					if (b.has_chain(i)) {
						line_from_center(cx, cy, i, len, chain_color);
					}
				}
			}
		}
	}
	//
	private void line_from_center(int cx, int cy, int rot, int len, int color) {
		int dx = Constant.movelist[rot][0];
		int dy = Constant.movelist[rot][1];
		for (int k = 1; k <= len; k++) {
			px[(cy + dy * k) * tw + cx + dx * k] = color;
		}
	}
}
