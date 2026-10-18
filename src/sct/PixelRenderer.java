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
		int s = Constant.size;
		int ww = Constant.world_scale[0];
		int wh = Constant.world_scale[1];
		if (tile == null || tw != ww * s || th != wh * s) {//тайл создается один раз, а не на каждый кадр
			tw = ww * s;
			th = wh * s;
			tile = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
			px = ((DataBufferInt) tile.getRaster().getDataBuffer()).getData();
		}
		draw_background(w, ww, wh, s);
		if (w.render) {
			draw_bots(w, s);
			Graphics2D g = tile.createGraphics();
			if (w.draw_type == 7) {//связи королев с подчиненными и сами королевы поверх
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
			Bot sel = w.selection;
			if (sel != null) {//выбранный бот
				g.setColor(Color.RED);
				g.setStroke(new BasicStroke(2));
				g.drawRect(sel.xpos * s - 2, sel.ypos * s - 2, s + 4, s + 4);
			}
			Bot prev = w.prev_selection;
			if (prev != null && prev.killed == 0) {//предыдущий выбранный — для сравнения геномов
				g.setColor(Color.YELLOW);
				g.setStroke(new BasicStroke(1));
				g.drawRect(prev.xpos * s - 2, prev.ypos * s - 2, s + 4, s + 4);
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
	private void draw_background(WorldBio w, int ww, int wh, int s) {
		int mode = w.res_draw_type;
		if (mode == 0) {
			Arrays.fill(px, Theme.world_bg);
			return;
		}
		double ox_max = Constant.ox_render_maximum_coeff;
		double co2_max = Constant.co2_render_maximum_coeff;
		double die = Constant.org_die_level;
		for (int x = 0; x < ww; x++) {
			for (int y = 0; y < wh; y++) {
				int c;
				double org = w.org_map[x][y];
				if (mode == 1) {//кислород
					c = Theme.mix(Theme.world_bg, Theme.oxygen, w.oxygen_map[x][y] / ox_max);
				}else if (mode == 2) {//органика с кислородом поверх
					c = Theme.mix(Theme.world_bg, Theme.organics, org / die);
					c = Theme.mix(c, Theme.oxygen, w.oxygen_map[x][y] / ox_max * (210 / 255.0));
				}else if (mode == 3) {//минералы
					c = Theme.mix(Theme.world_bg, Theme.minerals, w.mnr_map[x][y] / 1000.0);
				}else if (mode == 4) {//углекислый газ
					c = Theme.mix(Theme.world_bg, Theme.co2, w.co2_map[x][y] / co2_max);
				}else {//высоты
					c = Theme.mix(Theme.world_bg, Theme.minerals, w.height_map[x][y] / 1000.0);
				}
				if (mode != 3 && mode != 5 && org >= die) {
					c = Theme.dead_zone;
				}
				fill_cell(x, y, s, c);
			}
		}
	}
	//
	private void draw_bots(WorldBio w, int s) {
		int dt = w.draw_type;
		int half = s / 2;
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
			line_from_center(cx, cy, b.rotate, half, dir_color);
			if (dt == 7) {
				for (int i = 0; i < 8; i++) {
					if (b.has_chain(i)) {
						line_from_center(cx, cy, i, half, chain_color);
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
