package sct;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class WorldBioControler extends JPanel{

	protected WorldBio world;
	protected int width;
	protected int height;

	//раскладка: элементы идут сверху вниз по одному в ряд, поэтому пересечься не могут
	private static final int padding = 4;
	private static final int button_h = 26;
	private static final int label_h = 18;
	private static final int group_gap = 10;
	private int cursor_y = padding;

	private JButton button_start_stop = new JButton("Stop");
	private JButton button_render_border = new JButton("Render Border: Off");
	private JButton button_render_bot = new JButton("Render Bots: On");
	private JLabel label_bot_count = new JLabel();
	private JLabel label_steps = new JLabel();
	private JLabel label_ms = new JLabel();
	private JLabel label_speed = new JLabel();
	private JLabel label_draw_type = new JLabel();
	private JLabel label_res_draw_type = new JLabel();
	private JLabel label_sel_pos = new JLabel();
	private JLabel label_sel_energy = new JLabel();
	private JLabel label_sel_type = new JLabel();
	private JLabel label_sel_genom = new JLabel();
	private JPanel sel_color_box = new JPanel();

	public WorldBioControler(WorldBio world, int width, int height) {
		this.world = world;
		this.width = width;
		this.height = height;

        setPreferredSize(new Dimension(width, height));
        setLayout(null);

        //
        //управление симуляцией
        //
        header("Simulation");
		button_start_stop.addActionListener(new start_stop());
        place(button_start_stop, button_h);

        JButton new_population_button = new JButton("New population");
        new_population_button.addActionListener(new nwp());
        place(new_population_button, button_h);

        button_render_border.addActionListener(new render_border());
        place(button_render_border, button_h);

        button_render_bot.addActionListener(new render_bot());
        place(button_render_bot, button_h);

        JButton step_button = new JButton("Step (N)");
        step_button.addActionListener(e -> world.request_step());
        place(step_button, button_h);

        JButton slower_button = new JButton("<< (,)");
        slower_button.addActionListener(e -> world.slower());
        JButton faster_button = new JButton("(.) >>");
        faster_button.addActionListener(e -> world.faster());
        place_pair(slower_button, faster_button, button_h);
        place(label_speed, label_h);

        JButton theme_button = new JButton("Theme: dark / light");
        theme_button.addActionListener(e -> Theme.toggle(SwingUtilities.getWindowAncestor(this)));
        place(theme_button, button_h);

        //
        //статистика
        //
        header("Stats");
        place(label_bot_count, label_h);
        place(label_steps, label_h);
        place(label_ms, label_h);
        place(label_draw_type, label_h);
        place(label_res_draw_type, label_h);

        //
        //смена режимов отрисовки
        //
        header("Bot view");
        draw_type_button("Predators", 0);
        draw_type_button("Energy", 2);
        draw_type_button("Clans", 3);
        draw_type_button("Age", 4);
        draw_type_button("Color", 1);
        draw_type_button("Type", 5);
        draw_type_button("Chain", 6);
        draw_type_button("Queen", 7);

        //
        //смена режимов отрисовки фона
        //
        header("Background");
        res_draw_type_button("None", 0);
        res_draw_type_button("Oxygen", 1);
        res_draw_type_button("Org + O2", 2);
        res_draw_type_button("Minerals", 3);
        res_draw_type_button("Co2", 4);

        //
        //выбранный бот
        //
        header("Selection");
        sel_color_box.setBounds(width - 35, cursor_y, 25, label_h);
        add(sel_color_box);
        place(label_sel_pos, label_h);
        place(label_sel_energy, label_h);
        place(label_sel_type, label_h);
        place(label_sel_genom, label_h);

        bind_key("SPACE", () -> button_start_stop.doClick());
        bind_key("N", world::request_step);
        bind_key("COMMA", world::slower);
        bind_key("PERIOD", world::faster);

        refresh();
        new Timer(100, e -> refresh()).start();//подписи обновляются по таймеру, а не из paintComponent
	}

	private void place(JComponent c, int h) {//ставит элемент в следующий свободный ряд
		if (c instanceof JButton) {
			c.setFocusable(false);//иначе пробел нажимает кнопку в фокусе вместо паузы
		}
		c.setBounds(padding, cursor_y, width - padding * 2, h);
		add(c);
		cursor_y += h + padding;
	}
	private void place_pair(JComponent a, JComponent b, int h) {//два элемента в одном ряду
		a.setFocusable(false);
		b.setFocusable(false);
		int half = (width - padding * 3) / 2;
		a.setBounds(padding, cursor_y, half, h);
		b.setBounds(padding * 2 + half, cursor_y, half, h);
		add(a);
		add(b);
		cursor_y += h + padding;
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
	private void draw_type_button(String text, int draw_type) {
		JButton b = new JButton(text);
		b.addActionListener(new change_draw_type(draw_type));
		place(b, button_h);
	}
	private void res_draw_type_button(String text, int res_draw_type) {
		JButton b = new JButton(text);
		b.addActionListener(new change_res_draw_type(res_draw_type));
		place(b, button_h);
	}

	private void refresh() {
		label_bot_count.setText("bot count: " + world.b_count);
		label_steps.setText("steps: " + world.steps);
		label_ms.setText(String.format("ms/step: %.1f", world.ms_per_step));
		label_speed.setText("speed: " + world.speed_name());
		label_draw_type.setText("bots: " + Constant.draw_type_names[world.draw_type]);
		label_res_draw_type.setText("background: " + Constant.res_type_names[world.res_draw_type]);

        Bot s = world.selection;//выбранный бот
        if (s != null) {
        	label_sel_pos.setText("position: [" + s.xpos + ", " + s.ypos + "]");
        	label_sel_energy.setText("energy: " + (int) s.energy + ", age: " + (int) s.age);
        	label_sel_type.setText("type: " + s.type + ", queen: " + s.queen);
        	label_sel_genom.setText("genom: " + s.genom_current_idx + ", index: " + s.index);
        	sel_color_box.setBackground(s.color);
        	sel_color_box.setVisible(true);
        }else {
        	label_sel_pos.setText("none");
        	label_sel_energy.setText("");
        	label_sel_type.setText("");
        	label_sel_genom.setText("");
        	sel_color_box.setVisible(false);
        }
	}

	private class start_stop implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.pause = !world.pause;
			if (world.pause) {
				button_start_stop.setText("Start");
			}else {
				button_start_stop.setText("Stop");
			}
		}
	}
	private class render_border implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.render_border = !world.render_border;
			if (world.render_border) {
				button_render_border.setText("Render Border: On");
			}else {
				button_render_border.setText("Render Border: Off");
			}
		}
	}
	private class render_bot implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.render = !world.render;
			if (world.render) {
				button_render_bot.setText("Render Bots: On");
			}else {
				button_render_bot.setText("Render Bots: Off");
			}
		}
	}
	private class nwp implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.newPopulation();
		}
	}

	//
	private class change_draw_type implements ActionListener{//смена режима отрисовки(берется из параметра)
		int number;
		private change_draw_type(int new_number){
			number = new_number;
		}
		public void actionPerformed(ActionEvent e) {
			world.draw_type = number;
		}
	}
	private class change_res_draw_type implements ActionListener{//смена режима отрисовки фона(берется из параметра)
		int number;
		private change_res_draw_type(int new_number){
			number = new_number;
		}
		public void actionPerformed(ActionEvent e) {
			world.res_draw_type = number;
		}
	}
}
