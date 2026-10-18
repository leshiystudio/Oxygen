package sct;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

public class WorldBioControler extends JPanel{
	private static final long serialVersionUID = 1L;

	protected WorldBio world;
	protected GraphPanel graphs;
	protected int width;
	protected int height;

	//раскладка: элементы идут сверху вниз, по одному или по два в ряд, поэтому пересечься не могут
	private static final int padding = 3;
	private static final int button_h = 24;
	private static final int label_h = 16;
	private static final int group_gap = 6;
	private int cursor_y = padding;

	private JButton button_start_stop = new JButton("Stop");
	private JButton button_render_border = new JButton("Border: Off");
	private JButton button_render_bot = new JButton("Bots: On");
	private JComboBox<String> tool_box = new JComboBox<String>();
	private JLabel label_bot_count = new JLabel();
	private JLabel label_steps = new JLabel();
	private JLabel label_ms = new JLabel();
	private JLabel label_speed = new JLabel();
	private JLabel label_balance = new JLabel();
	private JLabel label_clans = new JLabel();
	private JLabel label_draw_type = new JLabel();
	private JLabel label_res_draw_type = new JLabel();
	private JLabel label_sel_pos = new JLabel();
	private JLabel label_sel_energy = new JLabel();
	private JLabel label_sel_type = new JLabel();
	private JLabel label_sel_genom = new JLabel();
	private JLabel label_sel_kin = new JLabel();
	private JPanel sel_color_box = new JPanel();

	public WorldBioControler(WorldBio world, GraphPanel graphs, int width, int height) {
		this.world = world;
		this.graphs = graphs;
		this.width = width;
		this.height = height;

        setPreferredSize(new Dimension(width, height));
        setLayout(null);

        //
        //управление симуляцией
        //
        header("Simulation");
		button_start_stop.addActionListener(e -> start_stop());
        JButton new_population_button = new JButton("Again");
        new_population_button.setToolTipText("new world with the same settings");
        new_population_button.addActionListener(e -> world.newPopulation());
        place_pair(button_start_stop, new_population_button);

        JButton new_world_button = new JButton("New world...");
        new_world_button.addActionListener(e -> NewWorldDialog.open(SwingUtilities.getWindowAncestor(this), world));
        JButton help_button = new JButton("Help (H)");
        help_button.addActionListener(e -> HelpDialog.toggle(SwingUtilities.getWindowAncestor(this)));
        place_pair(new_world_button, help_button);

        JButton step_button = new JButton("Step (N)");
        step_button.addActionListener(e -> world.request_step());
        JButton theme_button = new JButton("Theme");
        theme_button.addActionListener(e -> Theme.toggle(SwingUtilities.getWindowAncestor(this)));
        place_pair(step_button, theme_button);

        JButton slower_button = new JButton("slower ,");
        slower_button.addActionListener(e -> world.slower());
        JButton faster_button = new JButton(". faster");
        faster_button.addActionListener(e -> world.faster());
        place_pair(slower_button, faster_button);
        place(label_speed, label_h);

        button_render_border.addActionListener(e -> {
        	world.render_border = !world.render_border;
        	button_render_border.setText("Border: " + (world.render_border ? "On" : "Off"));
        });
        button_render_bot.addActionListener(e -> {
        	world.render = !world.render;
        	button_render_bot.setText("Bots: " + (world.render ? "On" : "Off"));
        });
        place_pair(button_render_border, button_render_bot);

        JButton graphs_button = new JButton("Graphs (M)");
        graphs_button.addActionListener(e -> toggle_graphs());
        JButton genome_button = new JButton("Genome");
        genome_button.addActionListener(e -> GenomeView.show(SwingUtilities.getWindowAncestor(this), world));
        place_pair(graphs_button, genome_button);

        //
        //статистика
        //
        header("Stats");
        place(label_bot_count, label_h);
        place(label_steps, label_h);
        place(label_ms, label_h);
        place(label_balance, label_h);
        place(label_clans, label_h);
        place(label_draw_type, label_h);
        place(label_res_draw_type, label_h);

        //
        //смена режимов отрисовки
        //
        header("Bot view");
        place_pair(draw_type_button("Predators", 0), draw_type_button("Energy", 2));
        place_pair(draw_type_button("Clans", 3), draw_type_button("Kin", 8));
        place_pair(draw_type_button("Age", 4), draw_type_button("Color", 1));
        place_pair(draw_type_button("Type", 5), draw_type_button("Chain", 6));
        place_pair(draw_type_button("Queen", 7), draw_type_button("Population", 9));

        //
        //смена режимов отрисовки фона
        //
        header("Background");
        place_pair(res_draw_type_button("None", 0), res_draw_type_button("Oxygen", 1));
        place_pair(res_draw_type_button("Org + O2", 2), res_draw_type_button("Minerals / soil", 3));
        place_pair(res_draw_type_button("Co2", 4), new JLabel());

        //
        //инструмент правой кнопки мыши
        //
        header("Tool (right click)");
        for (Tools.Tool t : Tools.all) {
        	tool_box.addItem(t.name());
        }
        tool_box.setFocusable(false);
        tool_box.addActionListener(e -> world.tool_idx = Math.max(0, tool_box.getSelectedIndex()));
        place(tool_box, button_h);
        JButton reset_tool_button = new JButton("Reset (Esc)");
        reset_tool_button.addActionListener(e -> world.reset_tool());
        JButton next_tool_button = new JButton("Next ]");
        next_tool_button.addActionListener(e -> world.next_tool(1));
        place_pair(reset_tool_button, next_tool_button);

        //
        //сохранение
        //
        header("World");
        JButton save_button = new JButton("Save");
        save_button.addActionListener(e -> save_world());
        JButton load_button = new JButton("Load");
        load_button.addActionListener(e -> load_world());
        place_pair(save_button, load_button);
        JButton csv_button = new JButton("History -> CSV");
        csv_button.addActionListener(e -> save_csv());
        place(csv_button, button_h);

        //
        //выбранный бот
        //
        header("Selection");
        sel_color_box.setBounds(width - 30, cursor_y, 22, label_h);
        add(sel_color_box);
        place(label_sel_pos, label_h);
        place(label_sel_energy, label_h);
        place(label_sel_type, label_h);
        place(label_sel_genom, label_h);
        place(label_sel_kin, label_h);

        //
        //клавиши
        //
        bind_key("SPACE", () -> button_start_stop.doClick());
        bind_key("N", world::request_step);
        bind_key("COMMA", world::slower);
        bind_key("PERIOD", world::faster);
        bind_key("M", this::toggle_graphs);
        bind_key("L", () -> graphs.next_length());
        bind_key("OPEN_BRACKET", () -> world.next_tool(-1));
        bind_key("CLOSE_BRACKET", () -> world.next_tool(1));
        bind_key("ESCAPE", world::reset_tool);
        bind_key("H", () -> HelpDialog.toggle(SwingUtilities.getWindowAncestor(this)));
        for (int i = 1; i <= 9; i++) {//10-я группа (популяции) — галочкой
        	int group = i - 1;
        	bind_key(String.valueOf(i), () -> graphs.toggle_group(group));
        }

        refresh();
        new Timer(100, e -> refresh()).start();//подписи обновляются по таймеру, а не из paintComponent
	}

	private void place(JComponent c, int h) {//ставит элемент в следующий свободный ряд
		c.setFocusable(false);//иначе пробел нажимает кнопку в фокусе вместо паузы
		c.setBounds(padding, cursor_y, width - padding * 2, h);
		add(c);
		cursor_y += h + padding;
	}
	private void place_pair(JComponent a, JComponent b) {//два элемента в одном ряду
		a.setFocusable(false);
		b.setFocusable(false);
		int half = (width - padding * 3) / 2;
		a.setBounds(padding, cursor_y, half, button_h);
		b.setBounds(padding * 2 + half, cursor_y, half, button_h);
		add(a);
		add(b);
		cursor_y += button_h + padding;
	}
	private void bind_key(String key, Runnable action) {//клавиша работает, где бы ни был фокус в окне
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
		getActionMap().put(key, new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}
	private void header(String text) {//заголовок группы с отступом от предыдущей
		if (cursor_y > padding) {
			cursor_y += group_gap;
		}
		JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.BOLD));
		place(l, label_h);
	}
	private JButton draw_type_button(String text, int draw_type) {
		JButton b = new JButton(text);
		b.addActionListener(e -> world.draw_type = draw_type);
		return b;
	}
	private JButton res_draw_type_button(String text, int res_draw_type) {
		JButton b = new JButton(text);
		b.addActionListener(e -> world.res_draw_type = res_draw_type);
		return b;
	}
	//
	private void start_stop() {
		world.pause = !world.pause;
		button_start_stop.setText(world.pause ? "Start" : "Stop");
	}
	private void toggle_graphs() {
		graphs.setVisible(!graphs.isVisible());
		graphs.getParent().revalidate();
	}
	//
	private JFileChooser chooser(String ext, String what) {
		JFileChooser fc = new JFileChooser(new File("saved worlds"));
		fc.setFileFilter(new FileNameExtensionFilter(what, ext));
		return fc;
	}
	private void save_world() {
		JFileChooser fc = chooser("oxw", "Oxygen world (*.oxw)");
		fc.setSelectedFile(new File("saved worlds", "world_" + Constant.world_scale[0] + "x" + Constant.world_scale[1] + "_" + world.steps + ".oxw"));
		if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File f = fc.getSelectedFile();
			if (!f.getName().endsWith(".oxw")) {
				f = new File(f.getPath() + ".oxw");
			}
			try {
				WorldIO.save(world, f);
			}catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Не удалось сохранить: " + ex.getMessage());
			}
		}
	}
	private void load_world() {
		JFileChooser fc = chooser("oxw", "Oxygen world (*.oxw)");
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				WorldIO.load(world, fc.getSelectedFile());
			}catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Не удалось загрузить: " + ex.getMessage());
			}
		}
	}
	private void save_csv() {
		JFileChooser fc = chooser("csv", "CSV (*.csv)");
		fc.setSelectedFile(new File("saved worlds", "history_" + world.steps + ".csv"));
		if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			String csv;
			world.lock.lock();
			try {
				csv = History.csv();
			}finally {
				world.lock.unlock();
			}
			try {
				Files.writeString(fc.getSelectedFile().toPath(), csv, StandardCharsets.UTF_8);
			}catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Не удалось сохранить: " + ex.getMessage());
			}
		}
	}

	private void refresh() {
		label_bot_count.setText("bots: " + world.b_count + ", seeds: " + world.org_count + (world.plants != null ? ", plants: " + world.plant_count : ""));
		label_steps.setText("steps: " + world.steps + (Presets.seed >= 0 ? ", seed " + Presets.seed : ""));
		label_ms.setText(String.format("ms/step: %.1f", world.ms_per_step));
		label_speed.setText("speed: " + world.speed_name());
		label_balance.setText(String.format("balance diff: %.2g", Ledger.total_diff));
		label_clans.setText("kin clans: " + KinClans.clans + ", genomes: " + KinClans.unique_genomes);
		label_draw_type.setText("bots: " + Constant.draw_type_names[world.draw_type]);
		label_res_draw_type.setText("background: " + Constant.res_type_names[world.res_draw_type]);
		if (tool_box.getSelectedIndex() != world.tool_idx) {
			tool_box.setSelectedIndex(world.tool_idx);
		}

        Bot s = world.selection;//выбранный бот
        if (s != null) {
        	label_sel_pos.setText("position: [" + s.xpos + ", " + s.ypos + "]");
        	label_sel_energy.setText("energy: " + (int) s.energy + ", age: " + (int) s.age);
        	label_sel_type.setText("type: " + s.type + ", queen: " + s.queen + ", chains: " + s.count_chains());
        	label_sel_genom.setText("genom: " + s.genom_current_idx + ", index: " + s.index);
        	Bot p = world.prev_selection;
        	String cmp = "";
        	if (p != null && p != s) {
        		int diff = s.count_different_genom(s.genom_array, p.genom_array);
        		cmp = ", vs prev: " + diff + (diff <= Constant.max_different_genom ? " own" : " alien");
        	}
        	label_sel_kin.setText("kin: " + s.kin_id + cmp);
        	sel_color_box.setBackground(s.color);
        	sel_color_box.setVisible(true);
        }else {
        	label_sel_pos.setText("none (left click a bot)");
        	label_sel_energy.setText("");
        	label_sel_type.setText("");
        	label_sel_genom.setText("");
        	label_sel_kin.setText("");
        	sel_color_box.setVisible(false);
        }
	}
}
