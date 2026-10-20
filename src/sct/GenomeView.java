package sct;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import sct.neuro.NeuroBrain;
import sct.plants.Plant;
import sct.plants.PlantConfig;

//Окно мозга выбранного существа. Показ зависит от типа мозга:
//  бот с геномом         — команды сеткой 8 x 8 (цвет — категория, белая рамка — текущая команда);
//  растение think_new    — геном 32 гена x 23 команды: цвет столбца — роль (типы потомков, проверки,
//                          действия, переходы), белая рамка — активный ген;
//  растение think_old    — тот же геном как линейная программа, белая рамка — указатель команды;
//  нейросеть (бот, растение) — веса слоев тепловой картой: синие отрицательные, красные положительные;
//  свой алгоритм         — геном тела с пометкой, что мозг его не использует.
//Наведите курсор — внизу расшифровка. Сравнение — с предыдущим выбранным (желтая рамка в мире).
public class GenomeView extends JDialog {
	private static final long serialVersionUID = 1L;
	private static GenomeView instance;

	public static void show(Window owner, WorldBio world) {
		if (instance == null || instance.world != world) {
			instance = new GenomeView(owner, world);
		}
		instance.setVisible(true);
		instance.toFront();
	}
	//
	private enum Mode { NONE, BOT_GENOME, PLANT_NEW, PLANT_OLD, NEURO }
	private final WorldBio world;
	private final View view = new View();
	private final JLabel title = new JLabel(" ");
	private final JLabel compare = new JLabel(" ");
	private final JLabel status = new JLabel(" ");
	//снимок выбранного существа (под блокировкой мира)
	private Mode mode = Mode.NONE;
	private String note = "";
	private int[][] bot_gen, bot_prev;
	private int bot_genom_idx, bot_index;
	private byte[] plant_gen, plant_prev;
	private int plant_gene, plant_cmd;
	private PlantConfig pc;
	private float[] weights;
	private int[] sizes;
	private String[] in_names, out_names;
	private int mx = -1, my = -1;//курсор над картинкой

	private GenomeView(Window owner, WorldBio world) {
		super(owner, "Brain");
		this.world = world;
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		JPanel south = new JPanel(new GridLayout(2, 1));
		south.add(compare);
		south.add(status);
		add(title, BorderLayout.NORTH);
		add(new JScrollPane(view), BorderLayout.CENTER);
		add(south, BorderLayout.SOUTH);
		setSize(1000, 680);
		setLocationRelativeTo(owner);
		view.addMouseMotionListener(new MouseAdapter() {
			public void mouseMoved(MouseEvent e) {
				mx = e.getX();
				my = e.getY();
				view.repaint();
			}
		});
		new Timer(250, e -> {
			if (isShowing()) {
				snapshot();
				view.repaint();
			}
		}).start();
	}
	//
	//СНИМОК
	//
	private void snapshot() {
		IBot o = world.selection;
		IBot p = world.prev_selection;
		Mode old = mode;
		world.lock.lock();
		try {
			mode = Mode.NONE;
			note = "";
			compare.setText(" ");
			if (o == null) {
				title.setText("no creature selected (left click a cell)");
			}else if (o instanceof Bot b && b.brain instanceof NeuroBrain<?> nb) {
				snapshot_neuro(nb, p instanceof Bot pb && pb.brain instanceof NeuroBrain<?> pn ? pn : null);
				title.setText(header(o) + " — neural network");
			}else if (o instanceof Plant pl && pl.brain instanceof NeuroBrain<?> nb) {
				snapshot_neuro(nb, p instanceof Plant pp && pp.brain instanceof NeuroBrain<?> pn ? pn : null);
				title.setText(header(o) + " — neural network");
			}else if (o instanceof Bot b) {
				mode = Mode.BOT_GENOME;
				bot_gen = copy(b.genom_array);
				bot_prev = p instanceof Bot pb && pb != b ? copy(pb.genom_array) : null;
				bot_genom_idx = b.genom_current_idx;
				bot_index = b.index;
				if (b.brain != null) {
					note = "  (brain '" + b.brain.name() + "' does not use the genome — shown for lineage)";
				}
				title.setText(header(o) + " — genome of commands" + note);
				if (bot_prev != null) {
					int diff = b.count_different_genom(b.genom_array, ((Bot) p).genom_array);
					compare.setText("vs previous (yellow): " + diff + " genes differ — " + (diff <= Constant.max_different_genom ? "own" : "alien")
						+ " (threshold " + Constant.max_different_genom + ")");
				}
			}else if (o instanceof Plant pl) {
				mode = WorldBio.brain_name(pl).equals("think_old") ? Mode.PLANT_OLD : Mode.PLANT_NEW;
				plant_gen = pl.genome.clone();
				plant_prev = p instanceof Plant pp && pp != pl ? pp.genome.clone() : null;
				plant_gene = pl.gen;
				plant_cmd = pl.cmd;
				pc = pl.w.c;
				title.setText(header(o) + (mode == Mode.PLANT_OLD ? " — linear genome (think_old)" : " — genome 32 genes x 23 commands (think_new)"));
				if (plant_prev != null) {
					int diff = 0;
					for (int i = 0; i < plant_gen.length && i < plant_prev.length; i++) {
						if (plant_gen[i] != plant_prev[i]) {
							diff++;
						}
					}
					compare.setText("vs previous (yellow): " + diff + " commands differ — " + (pl.same_kin(p) ? "own" : "alien")
						+ " (threshold " + pc.max_mind_diff_count + ")");
				}
			}else {
				title.setText(o.describe());
			}
		}finally {
			world.lock.unlock();
		}
		if (mode != old) {
			view.revalidate();
		}
	}
	private void snapshot_neuro(NeuroBrain<?> nb, NeuroBrain<?> prev) {
		mode = Mode.NEURO;
		weights = nb.net.w.clone();
		sizes = nb.net.sizes.clone();
		in_names = in_names(nb);
		out_names = out_names(nb);
		if (prev != null && prev != nb) {
			double dist = nb.net.distance(prev.net);
			compare.setText(String.format("vs previous (yellow): mean weight difference %.4f — %s (threshold %.4f)",
				dist, dist <= nb.c.kin_threshold ? "own" : "alien", nb.c.kin_threshold));
		}
		note = String.format("  layers %s, activation %s, memory %d, choice %s", java.util.Arrays.toString(sizes), nb.c.activation, nb.c.memory, nb.c.choice);
	}
	//имена входов и выходов сети (окно генома и сервер — sct.BrainInfo)
	static String[] in_names(NeuroBrain<?> nb) {
		String[] body_in = nb.body.inputs();
		String[] names = new String[nb.net.sizes[0]];
		for (int i = 0; i < names.length; i++) {
			names[i] = i < body_in.length ? body_in[i] : "memory " + (i - body_in.length);
		}
		return names;
	}
	static String[] out_names(NeuroBrain<?> nb) {
		String[] acts = nb.body.actions();
		String[] names = new String[nb.net.sizes[nb.net.sizes.length - 1]];
		int k = 0;
		for (String a : acts) {
			names[k++] = "action " + a;
		}
		for (int d = 0; d < nb.body.dirs(); d++) {
			names[k++] = "direction " + d;
		}
		for (int p = 0; p < nb.body.params(); p++) {
			names[k++] = "param " + p;
		}
		while (k < names.length) {
			names[k] = "memory " + (k - acts.length - nb.body.dirs() - nb.body.params());
			k++;
		}
		return names;
	}
	static String header(IBot o) {
		Population p = o.population();
		return (p != null ? p.name : o.species().title) + " [" + o.cell_x() + ", " + o.cell_y() + "], brain " + WorldBio.brain_name(o);
	}
	private static int[][] copy(int[][] g) {
		int[][] c = new int[g.length][];
		for (int i = 0; i < g.length; i++) {
			c[i] = g[i].clone();
		}
		return c;
	}
	//
	//РИСОВАНИЕ
	//
	private class View extends JPanel {
		private static final long serialVersionUID = 1L;
		private String hover;

		public Dimension getPreferredSize() {
			switch (mode) {
				case BOT_GENOME: return new Dimension(8 + bot_gen.length * (8 * 34 + 16), 30 + ((Constant.mind_size + 7) / 8) * 34);
				case PLANT_NEW: case PLANT_OLD: return new Dimension(40 + pc.genome_cols * 26, 30 + pc.genome_rows * 18);
				case NEURO: {
					int h = 10;
					for (int l = 1; l < sizes.length; l++) {
						h += 22 + sizes[l] * neuro_cell(sizes[l - 1] + 1) + 8;
					}
					return new Dimension(900, h);
				}
				default: return new Dimension(400, 200);
			}
		}
		public void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setColor(Theme.graph_bg);
			g.fillRect(0, 0, getWidth(), getHeight());
			hover = null;
			switch (mode) {
				case BOT_GENOME: paint_bot(g); break;
				case PLANT_NEW: case PLANT_OLD: paint_plant(g); break;
				case NEURO: paint_neuro(g); break;
				default: break;
			}
			status.setText(hover != null ? hover : "hover a cell to decode it");
		}
		private boolean over(int x, int y, int w, int h) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
		//бот: геном команд
		private void paint_bot(Graphics2D g) {
			int cell = 34, cols = 8;
			g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
			for (int gi = 0; gi < bot_gen.length; gi++) {
				int x0 = 8 + gi * (cols * cell + 16);
				g.setColor(Theme.panel_fg);
				g.drawString("genome " + gi + (gi == bot_genom_idx ? "  (active)" : ""), x0, 15);
				for (int i = 0; i < bot_gen[gi].length; i++) {
					int v = bot_gen[gi][i];
					int x = x0 + (i % cols) * cell;
					int y = 22 + (i / cols) * cell;
					g.setColor(Commands.category_colors[Commands.category(v)]);
					g.fillRect(x + 1, y + 1, cell - 2, cell - 2);
					g.setColor(Color.WHITE);
					g.drawString(String.valueOf(v), x + 8, y + 21);
					if (bot_prev != null && bot_prev[gi][i] != v) {
						g.setColor(Color.RED);
						g.fillRect(x + 3, y + cell - 6, cell - 6, 3);
					}
					if (gi == bot_genom_idx && i == bot_index) {
						frame(g, x, y, cell, cell);
					}
					if (over(x, y, cell, cell)) {
						hover = "genome " + gi + " [" + i + "] = " + v + ": " + Commands.name(v) + "   (" + Commands.category_names[Commands.category(v)] + ")";
					}
				}
			}
		}
		//растение: геном biosim
		private void paint_plant(Graphics2D g) {
			int cw = 26, ch = 18, x0 = 34, y0 = 22;
			int rows = pc.genome_rows, cols = pc.genome_cols;
			g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
			g.setColor(Theme.panel_fg);
			for (int c = 0; c < cols; c++) {
				g.drawString(String.valueOf(c), x0 + c * cw + 7, y0 - 6);
			}
			int active = Math.floorMod(plant_gene, rows);
			for (int r = 0; r < rows; r++) {
				g.setColor(mode == Mode.PLANT_NEW && r == active ? Color.WHITE : Theme.panel_fg);
				g.drawString(String.valueOf(r), 6, y0 + r * ch + 13);
				for (int c = 0; c < cols; c++) {
					int idx = r * cols + c;
					if (idx >= plant_gen.length) {
						continue;
					}
					int v = plant_gen[idx] & 0xFF;
					int x = x0 + c * cw;
					int y = y0 + r * ch;
					g.setColor(mode == Mode.PLANT_NEW ? new_color(pc, c, v) : old_color(v));
					g.fillRect(x + 1, y + 1, cw - 2, ch - 2);
					g.setColor(Color.WHITE);
					g.drawString(String.valueOf(v), x + 3, y + 13);
					if (plant_prev != null && idx < plant_prev.length && plant_prev[idx] != plant_gen[idx]) {
						g.setColor(Color.RED);
						g.fillRect(x + 2, y + ch - 4, cw - 4, 2);
					}
					if (over(x, y, cw, ch)) {
						hover = (mode == Mode.PLANT_NEW ? "gene " + r + ", command " + c + " = " + v + ": " + new_decode(pc, c, v)
							: "command " + idx + " = " + v + ": " + old_decode(v));
					}
				}
			}
			if (mode == Mode.PLANT_NEW) {
				frame(g, x0, y0 + active * ch, cols * cw, ch);
			}else {
				int idx = Math.floorMod(plant_cmd, plant_gen.length);
				frame(g, x0 + (idx % cols) * cw, y0 + (idx / cols) * ch, cw, ch);
			}
		}
		//нейросеть: веса слоев
		private int neuro_cell(int n_cols) {
			return Math.max(3, Math.min(14, (860 - 20) / Math.max(1, n_cols)));
		}
		private void paint_neuro(Graphics2D g) {
			g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
			int y = 10, k = 0;
			for (int l = 1; l < sizes.length; l++) {
				int n_in = sizes[l - 1], n_out = sizes[l];
				int cell = neuro_cell(n_in + 1);
				g.setColor(Theme.panel_fg);
				g.drawString("layer " + l + ": " + n_in + " inputs + bias -> " + n_out + (l == sizes.length - 1 ? " outputs" : " neurons") + (l == 1 ? note : ""), 10, y + 12);
				y += 18;
				for (int j = 0; j < n_out; j++) {
					for (int i = 0; i <= n_in; i++) {
						float v = weights[k + j * (n_in + 1) + i];
						int x = 10 + i * cell;
						int yy = y + j * cell;
						g.setColor(weight_color(v));
						g.fillRect(x, yy, cell - (cell > 4 ? 1 : 0), cell - (cell > 4 ? 1 : 0));
						if (over(x, yy, cell, cell)) {
							String from = i == n_in ? "bias" : l == 1 ? in_names[i] : "neuron " + (l - 1) + "." + i;
							String to = l == sizes.length - 1 ? out_names[j] : "neuron " + l + "." + j;
							hover = String.format("layer %d: %s -> %s, weight %.3f", l, from, to, v);
						}
					}
				}
				k += n_out * (n_in + 1);
				y += n_out * cell + 8;
			}
		}
		private void frame(Graphics2D g, int x, int y, int w, int h) {
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2));
			g.drawRect(x, y, w - 1, h - 1);
			g.setStroke(new BasicStroke(1));
		}
	}
	//
	//РАСШИФРОВКА biosim (статические — ими же пользуется сервер, sct.BrainInfo)
	//
	private static final String[] checks = {"occupied left", "occupied ahead", "occupied right", "free left, ahead and right",
		"energy for two children", "alien ahead", "own ahead"};
	private static final String[] todos = {"shift soil energy left", "shift soil energy ahead", "shift soil energy right",
		"eat aliens around", "move ahead", "turn left", "turn right", "mutate (chance)", "absorb CO2 around (energy)"};
	private static final Color C_UNUSED = new Color(45, 45, 50), C_CHECK = new Color(120, 100, 160), C_BIRTH_MODE = new Color(90, 90, 100),
		C_ACTION = new Color(60, 150, 90), C_JUMP = new Color(60, 100, 170);
	private static final int[] plant_palette = {0x333338, 0xA0AAAA, 0xBE320A, 0xE6830A, 0x0AB01A, 0x6E3BFF};
	static int plant_type(PlantConfig pc, int v) {
		if (v < pc.type_ilive_max) {
			return Plant.ILIVE;
		}
		if (v < pc.type_leaf_max) {
			return Plant.LEAF;
		}
		if (v < pc.type_trans_max) {
			return Plant.TRANS;
		}
		if (v < pc.type_root_max) {
			return Plant.ROOT;
		}
		return Plant.EMPTY;
	}
	static Color new_color(PlantConfig pc, int c, int v) {
		switch (c) {
			case 0: case 1: case 2: return new Color(plant_palette[plant_type(pc, v)]);
			case 3: case 5: return v < pc.think_threshold ? C_CHECK : C_BIRTH_MODE;
			case 9: case 12: return C_ACTION;
			case 8: case 10: case 11: case 13: case 14: return C_JUMP;
			default: return C_UNUSED;
		}
	}
	static String new_decode(PlantConfig pc, int c, int v) {
		int rows = pc.genome_rows;
		switch (c) {
			case 0: case 1: case 2: return "child type " + (c == 0 ? "left" : c == 1 ? "ahead" : "right") + ": "
				+ (plant_type(pc, v) == Plant.EMPTY ? "none" : Plant.type_names[plant_type(pc, v)]) + "  (used when command 3 or 5 >= " + pc.think_threshold + ")";
			case 3: case 5: return v < pc.think_threshold
				? "check: " + (v % pc.check_mod < checks.length ? checks[v % pc.check_mod] : "always false")
				: ">= " + pc.think_threshold + " -> this gene breeds (commands 0..2)";
			case 9: return "action if both checks pass: " + todo(pc, v);
			case 10: return "next gene if that action succeeded: " + v % rows;
			case 11: return "next gene if that action failed: " + v % rows;
			case 12: return "action if a check failed: " + todo(pc, v) + (v > 2 ? "; before it — jump to the gene of command 8" : "");
			case 8: return "gene to jump to before command 12 (when command 12 > 2): " + v % rows;
			case 13: return "next gene if command 12 succeeded: " + v % rows;
			case 14: return "next gene if command 12 failed: " + v % rows;
			default: return "not used by think_new";
		}
	}
	private static String todo(PlantConfig pc, int v) {
		int k = v % pc.todo_mod;
		return k < todos.length ? todos[k] : "nothing";
	}
	static Color old_color(int v) {
		if (v < 3) {
			return new Color(0xE6830A);
		}
		if (v < 4) {
			return C_JUMP;
		}
		if (v < 8) {
			return new Color(200, 60, 60);
		}
		if (v == 23 || v == 24) {
			return C_CHECK;
		}
		if (v == 25) {
			return new Color(110, 110, 110);
		}
		if (v > 25) {
			return new Color(55, 55, 60);
		}
		return C_UNUSED;
	}
	static String old_decode(int v) {
		if (v < 3) {
			return "breed (children = cell+1 % 4, directions from " + v + ", type from cell+2) and move in direction " + v;
		}
		if (v < 4) {
			return "move in direction " + v;
		}
		if (v < 8) {
			return "eat aliens around";
		}
		if (v == 23) {
			return "if energy < cost_birth execute the command written in cell+1, else in cell+2";
		}
		if (v == 24) {
			return "if energy > value of cell+1 execute the command in cell+2, else in cell+3";
		}
		if (v == 25) {
			return "mutate (chance percent_mutation_am)";
		}
		if (v > 25) {
			return "jump pointer by " + v;
		}
		return "nothing";
	}
	static Color weight_color(float v) {
		double t = Math.min(1, Math.abs(v) / 2.0);
		int base = 40;
		return v >= 0 ? new Color(base + (int)(215 * t), base, base) : new Color(base, base, base + (int)(215 * t));
	}
}
