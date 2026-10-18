package sct;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.Timer;

//Живые графики внизу окна. Симуляция при этом не останавливается.
//Галочки (или клавиши 1-8) включают группы, кнопка — длину истории.
public class GraphPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	//
	private static final String[] group_names = {"cells", "energy", "events/step", "deaths/step", "genomes", "gases", "ms/step", "queens", "plants", "populations"};
	private static final int[][] group_series = {
		{History.BOTS, History.SEEDS},
		{History.ENERGY_CELLS, History.ORGANICS, History.ENERGY_TOTAL},
		{History.BIRTHS, History.DEATHS, History.MUTATIONS},
		{History.death_index(1), History.death_index(2), History.death_index(3), History.death_index(4),
			History.death_index(5), History.death_index(6), History.death_index(7), History.death_index(0),
			History.death_index(8), History.death_index(9), History.death_index(10), History.death_index(11)},
		{History.UNIQUE, History.CLANS},
		{History.OXYGEN, History.CO2},
		{History.MS},
		{History.QUEENS, History.SUBS},
		{History.PLANTS, History.CORPSES, History.SOIL},
		{History.POP0, History.POP0 + 1, History.POP0 + 2, History.POP0 + 3, History.POP0 + 4, History.POP0 + 5, History.POP0 + 6, History.POP0 + 7},
	};
	private static final Color[] palette = {
		new Color(80, 160, 255), new Color(255, 160, 60), new Color(90, 200, 120), new Color(230, 80, 90),
		new Color(180, 120, 230), new Color(230, 210, 80), new Color(80, 210, 210), new Color(160, 160, 160),
		new Color(240, 120, 200), new Color(120, 200, 90), new Color(200, 150, 100), new Color(110, 140, 240)
	};
	private static final int[] lengths = {300, 1000, 3000, 0};//0 — вся история
	//
	private final WorldBio world;
	private final JCheckBox[] boxes = new JCheckBox[group_names.length];
	private int length_idx = 1;
	private final JButton length_button = new JButton();
	private final Plot plot = new Plot();

	public GraphPanel(WorldBio world) {
		this.world = world;
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(100, 230));
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		for (int i = 0; i < group_names.length; i++) {
			boxes[i] = new JCheckBox((i + 1) + " " + group_names[i], i < 4);
			boxes[i].setFocusable(false);
			boxes[i].addActionListener(e -> plot.repaint());
			top.add(boxes[i]);
		}
		length_button.setFocusable(false);
		length_button.addActionListener(e -> {
			length_idx = (length_idx + 1) % lengths.length;
			update_length_text();
		});
		update_length_text();
		top.add(length_button);
		add(top, BorderLayout.NORTH);
		add(plot, BorderLayout.CENTER);
		new Timer(250, e -> {
			if (isShowing()) {
				plot.repaint();
			}
		}).start();
	}
	public void toggle_group(int i) {
		if (i >= 0 && i < boxes.length) {
			boxes[i].setSelected(!boxes[i].isSelected());
			plot.repaint();
		}
	}
	public void next_length() {
		length_button.doClick();
	}
	private void update_length_text() {
		length_button.setText("L: " + (lengths[length_idx] == 0 ? "all" : String.valueOf(lengths[length_idx])));
	}
	//
	private class Plot extends JPanel {
		private static final long serialVersionUID = 1L;

		public void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setColor(Theme.graph_bg);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int enabled = 0;
			for (JCheckBox b : boxes) {
				if (b.isSelected()) {
					enabled++;
				}
			}
			if (enabled == 0) {
				return;
			}
			int gw = getWidth() / enabled;
			int gx = 0;
			for (int gi = 0; gi < group_names.length; gi++) {
				if (!boxes[gi].isSelected()) {
					continue;
				}
				draw_group(g, gi, gx + 4, 4, gw - 8, getHeight() - 8);
				gx += gw;
			}
		}
		//
		private void draw_group(Graphics2D g, int gi, int x0, int y0, int w, int h) {
			int[] series = java.util.Arrays.stream(group_series[gi])//колонки популяций, которых нет в мире, не рисуются
				.filter(k -> !History.names[k].matches("pop [0-9]+")).toArray();
			if (series.length == 0) {
				return;
			}
			int len = lengths[length_idx];
			double[][] values = new double[series.length][];
			int first_step, last_step;
			world.lock.lock();//копия нужного куска истории; симуляция ждет не дольше копирования
			try {
				int n = History.size;
				int from = len == 0 ? 0 : Math.max(0, n - len);
				int count = n - from;
				if (count < 2) {
					return;
				}
				int points = Math.min(count, Math.max(2, w));//не больше точки на пиксель
				for (int s = 0; s < series.length; s++) {
					values[s] = new double[points];
					for (int p = 0; p < points; p++) {
						int a = from + (int)((long)p * count / points);
						int b = Math.max(a + 1, from + (int)((long)(p + 1) * count / points));
						double sum = 0;
						for (int k = a; k < b; k++) {
							sum += History.data[series[s]][k];
						}
						values[s][p] = sum / (b - a);
					}
				}
				first_step = History.step_of[from];
				last_step = History.step_of[n - 1];
			}finally {
				world.lock.unlock();
			}
			double max = 0;
			for (double[] v : values) {
				for (double d : v) {
					max = Math.max(max, d);
				}
			}
			if (max <= 0) {
				max = 1;
			}
			g.setColor(Theme.graph_grid);
			g.drawRect(x0, y0, w, h);
			for (int k = 1; k < 4; k++) {
				g.drawLine(x0, y0 + h * k / 4, x0 + w, y0 + h * k / 4);
			}
			int top = y0 + 30;
			int ph = h - 34;
			for (int s = 0; s < series.length; s++) {
				g.setColor(palette[s % palette.length]);
				double[] v = values[s];
				int px = -1, py = -1;
				for (int p = 0; p < v.length; p++) {
					int xx = x0 + (int)((long)p * w / Math.max(1, v.length - 1));
					int yy = top + ph - (int)(v[p] / max * ph);
					if (px >= 0) {
						g.drawLine(px, py, xx, yy);
					}
					px = xx;
					py = yy;
				}
			}
			g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
			g.setColor(Theme.panel_fg);
			g.drawString(group_names[gi] + "  max " + format(max) + "  steps " + first_step + ".." + last_step, x0 + 4, y0 + 13);
			int lx = x0 + 4;
			for (int s = 0; s < series.length; s++) {
				String label = History.names[series[s]] + " " + format(values[s][values[s].length - 1]);
				g.setColor(palette[s % palette.length]);
				g.drawString(label, lx, y0 + 26);
				lx += g.getFontMetrics().stringWidth(label) + 10;
			}
		}
		private String format(double v) {
			if (Math.abs(v) >= 1e6) {
				return String.format("%.2fM", v / 1e6);
			}
			if (Math.abs(v) >= 1e4) {
				return String.format("%.1fk", v / 1e3);
			}
			if (Math.abs(v) >= 10 || v == 0) {
				return String.format("%.0f", v);
			}
			return String.format("%.3g", v);
		}
	}
}
