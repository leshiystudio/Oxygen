package sct;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import sct.plants.PlantAction;
import sct.plants.PlantConfig;

//Диалог «Новый мир»: все значения уже проставлены (настройки прошлого мира или запуска),
//можно сразу нажать «Создать». Вкладки: мир и виды, боты Oxygen, растения, нейро (этап 5).
public class NewWorldDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	public static void open(Window owner, WorldBio world) {
		NewWorldDialog d = new NewWorldDialog(owner, world);
		d.setVisible(true);
	}

	private NewWorldDialog(Window owner, WorldBio world) {
		super(owner, "New world", ModalityType.APPLICATION_MODAL);
		NewWorldPanel panel = new NewWorldPanel(world, this::dispose);
		setContentPane(panel);
		setSize(980, 700);
		setLocationRelativeTo(owner);
	}

	//содержимое диалога отдельно — его можно собрать и без окна (проверки, снимки)
	public static class NewWorldPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private final WorldBio world;
		private final Runnable close;
		private WorldSettings s;
		//
		private final JTextField field_w = new JTextField(5);
		private final JTextField field_h = new JTextField(5);
		private final JTextField field_seed = new JTextField(8);
		private final PopulationsModel populations_model = new PopulationsModel();
		private final JTable populations_table = new JTable(populations_model);
		private final InteractionModel interaction_model = new InteractionModel();
		private final JTable interaction_table = new JTable(interaction_model);
		private final ParamTable constants_table = new ParamTable();
		private final JComboBox<Population> bot_pop_box = new JComboBox<Population>();
		private final JPanel bot_actions = new JPanel(new GridLayout(0, 2, 4, 2));
		private final JComboBox<Population> plant_pop_box = new JComboBox<Population>();
		private final JComboBox<String> plant_preset_box = new JComboBox<String>(new String[]{"config", "original", "noleak", "balanced"});
		private final ParamTable plant_table = new ParamTable();
		private final JPanel plant_actions = new JPanel(new GridLayout(0, 2, 4, 2));
		private Population plant_shown;//чьи параметры сейчас в plant_table
		//в диалоге это задают популяции (численность, мозг, действия) — в таблице параметров не показываются
		private static final java.util.Set<String> by_populations = java.util.Set.of(
			"starting_bot_count", "starting_plant_count", "bot_brain", "bot_actions_off", "plant_actions_off");

		public NewWorldPanel(WorldBio world, Runnable close) {
			super(new BorderLayout(6, 6));
			this.world = world;
			this.close = close;
			this.s = (world.last_settings != null ? world.last_settings : WorldSettings.startup != null ? WorldSettings.startup : WorldSettings.capture()).copy();
			setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("World & species", world_tab());
			tabs.addTab("Oxygen bots", bots_tab());
			tabs.addTab("Plants (biosim)", plants_tab());
			tabs.addTab("Neuro", neuro_tab());
			add(tabs, BorderLayout.CENTER);
			add(buttons(), BorderLayout.SOUTH);
			load_into_ui();
		}
		//
		//ВКЛАДКИ
		//
		private JPanel world_tab() {
			JPanel p = new JPanel(new BorderLayout(6, 6));
			JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
			top.add(new JLabel("world width"));
			top.add(field_w);
			top.add(new JLabel("height"));
			top.add(field_h);
			top.add(new JLabel("   seed (-1 — random)"));
			top.add(field_seed);
			p.add(top, BorderLayout.NORTH);
			//популяции
			JPanel pops = new JPanel(new BorderLayout(4, 4));
			pops.setBorder(BorderFactory.createTitledBorder("Populations: body + brain + count (0 — not in the world)"));
			populations_table.setRowHeight(22);
			populations_table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<Species>(Species.values())));
			populations_table.getColumnModel().getColumn(2).setCellEditor(new BrainEditor());
			populations_table.getColumnModel().getColumn(4).setCellRenderer(new ColorRenderer());
			pops.add(new JScrollPane(populations_table), BorderLayout.CENTER);
			JPanel pb = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JButton add_bots = new JButton("+ Oxygen bots");
			add_bots.addActionListener(e -> add_population(Species.OXYGEN_BOT));
			JButton add_plants = new JButton("+ Plants");
			add_plants.addActionListener(e -> add_population(Species.PLANT));
			JButton remove = new JButton("Remove selected");
			remove.addActionListener(e -> remove_population(populations_table.getSelectedRow()));
			pb.add(add_bots);
			pb.add(add_plants);
			pb.add(remove);
			pops.add(pb, BorderLayout.SOUTH);
			//взаимодействия
			JPanel inter = new JPanel(new BorderLayout());
			inter.setBorder(BorderFactory.createTitledBorder("Interaction between populations (inside a population — rules of its species)"));
			interaction_table.setRowHeight(22);
			inter.add(new JScrollPane(interaction_table), BorderLayout.CENTER);
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pops, inter);
			split.setResizeWeight(0.45);
			p.add(split, BorderLayout.CENTER);
			return p;
		}
		private JPanel bots_tab() {
			JPanel p = new JPanel(new BorderLayout(6, 6));
			JPanel left = new JPanel(new BorderLayout(4, 4));
			left.setBorder(BorderFactory.createTitledBorder("Parameters of Oxygen bots (Constant, common to all bot populations)"));
			left.add(constants_table, BorderLayout.CENTER);
			JPanel right = new JPanel(new BorderLayout(4, 4));
			right.setBorder(BorderFactory.createTitledBorder("Actions of the population"));
			right.add(bot_pop_box, BorderLayout.NORTH);
			right.add(new JScrollPane(top_aligned(bot_actions)), BorderLayout.CENTER);
			bot_pop_box.addActionListener(e -> show_actions(bot_actions, (Population) bot_pop_box.getSelectedItem()));
			right.setPreferredSize(new Dimension(330, 100));
			p.add(left, BorderLayout.CENTER);
			p.add(right, BorderLayout.EAST);
			return p;
		}
		private JPanel plants_tab() {
			JPanel p = new JPanel(new BorderLayout(6, 6));
			JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
			top.add(new JLabel("population"));
			top.add(plant_pop_box);
			top.add(new JLabel("   biosim preset"));
			top.add(plant_preset_box);
			JButton apply_preset = new JButton("Apply preset");
			apply_preset.addActionListener(e -> {
				Population pop = (Population) plant_pop_box.getSelectedItem();
				if (pop != null) {
					pop.plant_config = PlantConfig.preset((String) plant_preset_box.getSelectedItem());
					show_plant_params(pop);
				}
			});
			top.add(apply_preset);
			p.add(top, BorderLayout.NORTH);
			JPanel center = new JPanel(new BorderLayout());
			center.setBorder(BorderFactory.createTitledBorder("Parameters (names as in biosim config.py)"));
			center.add(plant_table, BorderLayout.CENTER);
			p.add(center, BorderLayout.CENTER);
			JPanel right = new JPanel(new BorderLayout());
			right.setBorder(BorderFactory.createTitledBorder("Actions of the population"));
			right.add(new JScrollPane(top_aligned(plant_actions)), BorderLayout.CENTER);
			right.setPreferredSize(new Dimension(300, 100));
			p.add(right, BorderLayout.EAST);
			plant_pop_box.addActionListener(e -> {
				store_plant_params();
				Population pop = (Population) plant_pop_box.getSelectedItem();
				show_plant_params(pop);
				show_actions(plant_actions, pop);
			});
			return p;
		}
		private static JPanel top_aligned(JPanel content) {//галочки сверху, а не растянутые по высоте
			JPanel wrap = new JPanel(new BorderLayout());
			wrap.add(content, BorderLayout.NORTH);
			return wrap;
		}
		private JPanel neuro_tab() {
			JPanel p = new JPanel(new BorderLayout());
			p.add(new JLabel("<html><b>Neuro populations</b> (NeuroBot, NeuroPlant) — next stage: network layout, sensors, actions,"
				+ " mutation and kin threshold will be configured here.</html>"), BorderLayout.NORTH);
			return p;
		}
		private JPanel buttons() {
			JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			JButton defaults = new JButton("Defaults");
			defaults.addActionListener(e -> {
				s = (WorldSettings.startup != null ? WorldSettings.startup : WorldSettings.capture()).copy();
				load_into_ui();
			});
			JButton load = new JButton("Load...");
			load.addActionListener(e -> load_settings());
			JButton save = new JButton("Save...");
			save.addActionListener(e -> save_settings());
			JButton create = new JButton("Create");
			create.addActionListener(e -> create());
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e -> close.run());
			p.add(defaults);
			p.add(load);
			p.add(save);
			p.add(new JLabel("    "));
			p.add(create);
			p.add(cancel);
			return p;
		}
		//
		//ДАННЫЕ <-> ЭЛЕМЕНТЫ
		//
		private void load_into_ui() {
			String[] scale = s.constants.getOrDefault("world_scale", Constant.world_scale[0] + "," + Constant.world_scale[1]).split(",");
			field_w.setText(scale[0].trim());
			field_h.setText(scale[1].trim());
			field_seed.setText(String.valueOf(s.seed));
			List<String[]> rows = new ArrayList<String[]>();
			for (Map.Entry<String, String> e : s.constants.entrySet()) {
				if (!e.getKey().equals("world_scale") && !by_populations.contains(e.getKey())) {//размер — на первой вкладке, остальное — у популяций
					rows.add(new String[]{e.getKey(), e.getValue()});
				}
			}
			constants_table.set_rows(rows);
			plant_shown = null;
			populations_changed();
		}
		private void populations_changed() {//после правки списка популяций — обновить таблицы и выпадающие списки
			for (int i = 0; i < s.populations.size(); i++) {
				s.populations.get(i).id = i;
			}
			s.interaction = WorldSettings.copy(s.interaction, s.populations.size());
			populations_model.fireTableDataChanged();
			interaction_model.fireTableDataChanged();
			store_plant_params();
			plant_shown = null;
			fill_box(bot_pop_box, Species.OXYGEN_BOT);
			fill_box(plant_pop_box, Species.PLANT);
		}
		private void fill_box(JComboBox<Population> box, Species sp) {
			Object selected = box.getSelectedItem();
			box.removeAllItems();
			for (Population p : s.populations) {
				if (p.species == sp) {
					box.addItem(p);
				}
			}
			if (selected != null && s.populations.contains(selected)) {
				box.setSelectedItem(selected);
			}
		}
		private void show_actions(JPanel panel, Population pop) {
			panel.removeAll();
			if (pop != null) {
				Action<?>[] all = pop.species == Species.PLANT ? PlantAction.values() : BotAction.values();
				for (int i = 0; i < all.length; i++) {
					int idx = i;
					JCheckBox box = new JCheckBox(all[i].id() + "  (" + all[i].category() + ")", pop.actions_on[i]);
					box.addActionListener(e -> pop.actions_on[idx] = box.isSelected());
					panel.add(box);
				}
			}
			panel.revalidate();
			panel.repaint();
		}
		private void show_plant_params(Population pop) {
			plant_shown = pop;
			List<String[]> rows = new ArrayList<String[]>();
			if (pop != null) {
				for (Field f : PlantConfig.class.getFields()) {
					if (!Modifier.isStatic(f.getModifiers())) {
						rows.add(new String[]{f.getName(), WorldSettings.field_value(f, pop.plant_config)});
					}
				}
			}
			plant_table.set_rows(rows);
		}
		private void store_plant_params() {//правки таблицы растений — в настройки популяции
			if (plant_shown != null) {
				for (Map.Entry<String, String> e : plant_table.values().entrySet()) {
					plant_shown.plant_config.set(e.getKey(), e.getValue());
				}
			}
		}
		private void store_ui() {//все правки — в s; ошибка значения — исключение с понятным текстом
			if (populations_table.isEditing()) {
				populations_table.getCellEditor().stopCellEditing();
			}
			if (interaction_table.isEditing()) {
				interaction_table.getCellEditor().stopCellEditing();
			}
			int w = Integer.parseInt(field_w.getText().trim());
			int h = Integer.parseInt(field_h.getText().trim());
			if (w < 16 || h < 16) {
				throw new IllegalArgumentException("мир должен быть не меньше 16x16");
			}
			s.constants.clear();
			s.constants.put("world_scale", w + "," + h);
			s.constants.putAll(constants_table.values());
			s.seed = Long.parseLong(field_seed.getText().trim());
			store_plant_params();
		}
		//
		//КНОПКИ
		//
		private void add_population(Species sp) {
			String brain = sp == Species.PLANT ? "think_new" : "genome";
			Color c = Color.getHSBColor((float) Math.random(), 0.6f, 0.95f);
			s.populations.add(new Population(sp.title + " " + (s.populations.size() + 1), sp, brain, sp == Species.PLANT ? 1500 : 3000, c));
			populations_changed();
		}
		private void remove_population(int row) {
			if (row < 0 || row >= s.populations.size()) {
				return;
			}
			int n = s.populations.size();
			Interaction old = s.interaction;
			s.populations.remove(row);
			Interaction ia = new Interaction(n - 1);//таблица без удаленной строки и столбца
			for (int a = 0, na = 0; a < n; a++) {
				if (a == row) {
					continue;
				}
				for (int b = 0, nb = 0; b < n; b++) {
					if (b == row) {
						continue;
					}
					ia.eat[na][nb] = old.eat[a][b];
					ia.exchange[na][nb] = old.exchange[a][b];
					ia.share[na][nb] = old.share[a][b];
					ia.chain[na][nb] = old.chain[a][b];
					ia.seed_kills[na][nb] = old.seed_kills[a][b];
					nb++;
				}
				na++;
			}
			s.interaction = ia;
			populations_changed();
		}
		private void create() {
			try {
				store_ui();
				s.copy().apply(world);
				world.last_settings = s.copy();//запоминаются только настройки, с которыми мир создался
				close.run();
			}catch (RuntimeException e) {
				JOptionPane.showMessageDialog(this, "Не удалось создать мир: " + e.getMessage());
			}
		}
		private JFileChooser chooser() {
			JFileChooser fc = new JFileChooser(new File("saved worlds"));
			fc.setFileFilter(new FileNameExtensionFilter("World settings (*.properties)", "properties"));
			return fc;
		}
		private void save_settings() {
			try {
				store_ui();
			}catch (RuntimeException e) {
				JOptionPane.showMessageDialog(this, "Ошибка в настройках: " + e.getMessage());
				return;
			}
			JFileChooser fc = chooser();
			fc.setSelectedFile(new File("saved worlds", "settings.properties"));
			if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				File f = fc.getSelectedFile();
				if (!f.getName().endsWith(".properties")) {
					f = new File(f.getPath() + ".properties");
				}
				try {
					s.save(f);
				}catch (IOException e) {
					JOptionPane.showMessageDialog(this, "Не удалось сохранить: " + e.getMessage());
				}
			}
		}
		private void load_settings() {
			JFileChooser fc = chooser();
			if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				try {
					s = WorldSettings.load(fc.getSelectedFile());
					load_into_ui();
				}catch (IOException | RuntimeException e) {
					JOptionPane.showMessageDialog(this, "Не удалось загрузить: " + e.getMessage());
				}
			}
		}
		//
		//ТАБЛИЦЫ
		//
		private class PopulationsModel extends AbstractTableModel {
			private static final long serialVersionUID = 1L;
			private final String[] cols = {"name", "body", "brain", "count", "color"};
			public int getRowCount() { return s.populations.size(); }
			public int getColumnCount() { return cols.length; }
			public String getColumnName(int c) { return cols[c]; }
			public Class<?> getColumnClass(int c) { return c == 3 ? Integer.class : c == 1 ? Species.class : String.class; }
			public boolean isCellEditable(int r, int c) { return true; }
			public Object getValueAt(int r, int c) {
				Population p = s.populations.get(r);
				switch (c) {
					case 0: return p.name;
					case 1: return p.species;
					case 2: return p.brain;
					case 3: return p.count;
					default: return String.format("#%06X", p.color.getRGB() & 0xFFFFFF);
				}
			}
			public void setValueAt(Object v, int r, int c) {
				Population p = s.populations.get(r);
				try {
					switch (c) {
						case 0: p.name = String.valueOf(v); break;
						case 1:
							Species sp = (Species) v;
							if (sp != p.species) {//другое тело — новая популяция с настройками по умолчанию
								Population np = new Population(p.name, sp, sp == Species.PLANT ? "think_new" : "genome", p.count, p.color);
								s.populations.set(r, np);
							}
							break;
						case 2: p.brain = String.valueOf(v); break;
						case 3: p.count = Math.max(0, (Integer) v); break;
						default: p.color = Color.decode(String.valueOf(v).trim());
					}
				}catch (RuntimeException e) {
					JOptionPane.showMessageDialog(NewWorldPanel.this, "Неверное значение: " + v);
				}
				populations_changed();
			}
		}
		private class BrainEditor extends DefaultCellEditor {//список мозгов зависит от тела строки
			private static final long serialVersionUID = 1L;
			private final JComboBox<String> box;
			@SuppressWarnings("unchecked")
			BrainEditor() {
				super(new JComboBox<String>());
				box = (JComboBox<String>) getComponent();
			}
			public Component getTableCellEditorComponent(JTable t, Object value, boolean sel, int row, int col) {
				box.removeAllItems();
				Population p = s.populations.get(row);
				if (p.species == Species.PLANT) {
					box.addItem("think_new");
					box.addItem("think_old");
				}else {
					for (String b : Brains.for_bot.keySet()) {
						box.addItem(b);
					}
				}
				box.setSelectedItem(value);
				return box;
			}
		}
		private static class ColorRenderer extends DefaultTableCellRenderer {
			private static final long serialVersionUID = 1L;
			public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
				Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
				try {
					comp.setBackground(Color.decode(String.valueOf(v)));
				}catch (RuntimeException e) {
					comp.setBackground(Color.GRAY);
				}
				return comp;
			}
		}
		private class InteractionModel extends AbstractTableModel {//строка — пара «кто -> с кем» (разные популяции)
			private static final long serialVersionUID = 1L;
			private final String[] cols = {"who", "whom", "eat (efficiency 0..1)", "exchange (x)", "share energy", "chain", "seed kills"};
			private int[] pair(int r) {
				int n = s.populations.size();
				int k = 0;
				for (int a = 0; a < n; a++) {
					for (int b = 0; b < n; b++) {
						if (a != b && k++ == r) {
							return new int[]{a, b};
						}
					}
				}
				return new int[]{0, 0};
			}
			public int getRowCount() {
				int n = s.populations.size();
				return n * (n - 1);
			}
			public int getColumnCount() { return cols.length; }
			public String getColumnName(int c) { return cols[c]; }
			public Class<?> getColumnClass(int c) { return c < 2 ? String.class : c < 4 ? Double.class : Boolean.class; }
			public boolean isCellEditable(int r, int c) { return c >= 2; }
			public Object getValueAt(int r, int c) {
				int[] p = pair(r);
				Interaction ia = s.interaction;
				switch (c) {
					case 0: return s.populations.get(p[0]).name;
					case 1: return s.populations.get(p[1]).name;
					case 2: return ia.eat[p[0]][p[1]];
					case 3: return ia.exchange[p[0]][p[1]];
					case 4: return ia.share[p[0]][p[1]];
					case 5: return ia.chain[p[0]][p[1]];
					default: return ia.seed_kills[p[0]][p[1]];
				}
			}
			public void setValueAt(Object v, int r, int c) {
				int[] p = pair(r);
				Interaction ia = s.interaction;
				switch (c) {
					case 2: ia.eat[p[0]][p[1]] = Math.max(0, Math.min(1, (Double) v)); break;
					case 3: ia.exchange[p[0]][p[1]] = Math.max(0, (Double) v); break;
					case 4: ia.share[p[0]][p[1]] = (Boolean) v; break;
					case 5: ia.chain[p[0]][p[1]] = (Boolean) v; break;
					case 6: ia.seed_kills[p[0]][p[1]] = (Boolean) v; break;
				}
			}
		}
	}
}
