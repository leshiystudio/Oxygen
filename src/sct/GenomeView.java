package sct;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

//Окно генома выбранного бота: каждый геном сеткой 8 x 8 команд.
//Цвет — категория команды (Commands), белая рамка — текущая команда текущего генома,
//красная черта — ген отличается от предыдущего выбранного бота (желтая рамка в мире).
//Наведите курсор на команду — внизу появится расшифровка. Данные обновляются вживую.
public class GenomeView extends JDialog {
	private static final long serialVersionUID = 1L;
	private static GenomeView instance;
	//
	private static final int cell = 34;
	private static final int cols = 8;
	private final WorldBio world;
	private final JLabel status = new JLabel(" ");
	private final JLabel compare = new JLabel(" ");
	private int hover_genom = -1, hover_index = -1;

	public static void show(Window owner, WorldBio world) {
		if (instance == null || instance.world != world) {
			instance = new GenomeView(owner, world);
		}
		instance.setVisible(true);
		instance.toFront();
	}

	private GenomeView(Window owner, WorldBio world) {
		super(owner, "Genome");
		this.world = world;
		Grid grid = new Grid();
		int rows = (Constant.mind_size + cols - 1) / cols;
		grid.setPreferredSize(new Dimension(Constant.mind_count * (cols * cell + 16) + 8, rows * cell + 30));
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(compare, BorderLayout.NORTH);
		bottom.add(status, BorderLayout.SOUTH);
		add(grid, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(owner);
		new Timer(200, e -> {
			if (isShowing()) {
				grid.repaint();
				update_texts();
			}
		}).start();
	}
	//
	private void update_texts() {
		Bot s = world.selection;
		Bot p = world.prev_selection;
		if (s != null && p != null && p != s) {
			int diff = s.count_different_genom(s.genom_array, p.genom_array);
			compare.setText("vs previous (yellow): " + diff + " genes differ — " + (diff <= Constant.max_different_genom ? "own (свой)" : "alien (чужой)")
				+ ", threshold " + Constant.max_different_genom);
		}else {
			compare.setText("select another bot to compare (previous one is marked yellow)");
		}
		if (s != null && hover_genom >= 0 && hover_index < Constant.mind_size) {
			int v = s.genom_array[hover_genom][hover_index];
			status.setText("genome " + hover_genom + " [" + hover_index + "] = " + v + ": " + Commands.name(v)
				+ "   (" + Commands.category_names[Commands.category(v)] + ")");
		}else if (s == null) {
			status.setText("no bot selected");
		}else {
			status.setText("hover a command to decode it; p1, p2... — the following genes");
		}
	}
	//
	private class Grid extends JPanel {
		private static final long serialVersionUID = 1L;
		Grid() {
			addMouseMotionListener(new MouseAdapter() {
				public void mouseMoved(MouseEvent e) {
					int block = cols * cell + 16;
					int g = (e.getX() - 8) / block;
					int cx = (e.getX() - 8) % block;
					int cy = e.getY() - 22;
					if (g >= 0 && g < Constant.mind_count && cx >= 0 && cx < cols * cell && cy >= 0) {
						hover_genom = g;
						hover_index = (cy / cell) * cols + cx / cell;
					}else {
						hover_genom = -1;
					}
					update_texts();
				}
			});
		}
		public void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setColor(Theme.graph_bg);
			g.fillRect(0, 0, getWidth(), getHeight());
			Bot s = world.selection;
			if (s == null) {
				return;
			}
			Bot p = world.prev_selection;
			int[][] gen;
			int[][] prev = null;
			int cur_genom, cur_index;
			world.lock.lock();//снимок генома, пока бот стоит
			try {
				gen = new int[s.genom_array.length][];
				for (int i = 0; i < gen.length; i++) {
					gen[i] = s.genom_array[i].clone();
				}
				if (p != null && p != s) {
					prev = p.genom_array;
				}
				cur_genom = s.genom_current_idx;
				cur_index = s.index;
			}finally {
				world.lock.unlock();
			}
			g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
			for (int gi = 0; gi < gen.length; gi++) {
				int x0 = 8 + gi * (cols * cell + 16);
				g.setColor(Theme.panel_fg);
				g.drawString("genome " + gi + (gi == cur_genom ? "  (active)" : ""), x0, 15);
				for (int i = 0; i < gen[gi].length; i++) {
					int v = gen[gi][i];
					int x = x0 + (i % cols) * cell;
					int y = 22 + (i / cols) * cell;
					g.setColor(Commands.category_colors[Commands.category(v)]);
					g.fillRect(x + 1, y + 1, cell - 2, cell - 2);
					g.setColor(Color.WHITE);
					g.drawString(String.valueOf(v), x + 8, y + 21);
					if (prev != null && prev[gi][i] != v) {//отличие от предыдущего выбранного
						g.setColor(Color.RED);
						g.fillRect(x + 3, y + cell - 6, cell - 6, 3);
					}
					if (gi == cur_genom && i == cur_index) {//текущая команда
						g.setColor(Color.WHITE);
						g.setStroke(new BasicStroke(2));
						g.drawRect(x, y, cell - 1, cell - 1);
						g.setStroke(new BasicStroke(1));
					}
				}
			}
		}
	}
}
