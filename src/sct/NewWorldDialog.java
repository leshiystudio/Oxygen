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
		open(owner, NewWorldPanel.initial(world), NewWorldPanel.creator(world));
	}
	//initial — с чем открыть, create — что сделать по «Создать» (мир в этом окне или на сервере, см. RemoteControl)
	public static void open(Window owner, WorldSettings initial, java.util.function.Consumer<WorldSettings> create) {
		NewWorldDialog d = new NewWorldDialog(owner, initial, create);
		d.setVisible(true);
	}

	private NewWorldDialog(Window owner, WorldSettings initial, java.util.function.Consumer<WorldSettings> create) {
		super(owner, "New world", ModalityType.APPLICATION_MODAL);
		NewWorldPanel panel = new NewWorldPanel(initial, create, this::dispose);
		setContentPane(panel);
		setSize(980, 700);
		setLocationRelativeTo(owner);
	}

	//содержимое диалога отдельно — его можно собрать и без окна (проверки, снимки)
	public static class NewWorldPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private final java.util.function.Consumer<WorldSettings> create;
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
		private final ParamTable constants_table = new ParamTable(ParamDocs.constant);
		private final JComboBox<Population> bot_pop_box = new JComboBox<Population>();
		private final JPanel bot_actions = new JPanel(new GridLayout(0, 2, 4, 2));
		private final JComboBox<Population> plant_pop_box = new JComboBox<Population>();
		private final JComboBox<String> plant_preset_box = new JComboBox<String>(new String[]{"config", "original", "noleak", "balanced"});
		private final ParamTable plant_table = new ParamTable(ParamDocs.plant);
		private final JPanel plant_actions = new JPanel(new GridLayout(0, 2, 4, 2));
		private Population plant_shown;//чьи параметры сейчас в plant_table
		private final JComboBox<Population> neuro_pop_box = new JComboBox<Population>();
		private final ParamTable neuro_table = new ParamTable(ParamDocs.neuro);
		private final javax.swing.JTextArea neuro_sensors = new javax.swing.JTextArea();
		private Population neuro_shown;//чьи параметры сейчас в neuro_table
		//в диалоге это задают популяции (численность, мозг, действия) — в таблице параметров не показываются
		static final java.util.Set<String> by_populations = java.util.Set.of(
			"starting_bot_count", "starting_plant_count", "starting_fly_count", "bot_brain", "bot_actions_off", "plant_actions_off");
		//перезапуск и перенос лучших — на первой вкладке, а не в таблице параметров ботов
		static final java.util.Set<String> world_keys = java.util.Set.of("restart_mode", "restart_forced", "restart_steps", "elite", "elite_size", "elite_fields");
		private final JComboBox<String> restart_box = new JComboBox<String>(new String[]{"off", "all", "any"});
		private final javax.swing.JCheckBox forced_box = new javax.swing.JCheckBox("forced restart after");
		private final JTextField field_restart_steps = new JTextField(7);
		private final javax.swing.JCheckBox elite_box = new javax.swing.JCheckBox("carry the best: clone them into the new world");
		private final JTextField field_elite_size = new JTextField(4);
		private final javax.swing.JCheckBox[] elite_fields = new javax.swing.JCheckBox[Elite.FIELDS.length];
		//мухи (sct.neuro.FlyBrain) — своя вкладка, а не таблица параметров ботов
		static final java.util.Set<String> fly_keys = java.util.Set.of(
			"fly_mode", "fly_slots", "fly_recyclers", "fly_move_co2", "fly_host", "fly_port", "fly_python");
		private final JComboBox<String> fly_mode_box = new JComboBox<String>(new String[]{"single", "learn"});
		private final JLabel fly_mode_hint = new JLabel();
		private final JTextField field_fly_slots = new JTextField(4);
		private final JTextField field_fly_recyclers = new JTextField(4);
		private final JTextField field_fly_move_co2 = new JTextField(4);
		private final JTextField field_fly_host = new JTextField(14);
		private final JTextField field_fly_port = new JTextField(5);
		private final JTextField field_fly_python = new JTextField(14);
		//режим высот (Terrain) — на первой вкладке, а не в таблице параметров ботов
		static final String[] terrain_fields = {"temp_low", "temp_mid", "temp_high", "pht_t_min", "pht_t_opt", "pht_t_max", "gas_drift"};
		static final java.util.Set<String> terrain_keys = java.util.Set.of(
			"terrain", "temp_low", "temp_mid", "temp_high", "pht_t_min", "pht_t_opt", "pht_t_max", "gas_drift",
			"terrain_relief", "terrain_height_min", "terrain_height_max", "terrain_mountains", "terrain_sharpness", "terrain_size");
		//рельеф (Relief) — при создании мира, и при выключенном режиме высот: горы видны на фоне Height
		static final String[] relief_fields = {"terrain_height_min", "terrain_height_max", "terrain_mountains", "terrain_sharpness", "terrain_size"};
		private final JComboBox<String> relief_box = new JComboBox<String>(new String[]{"peaks", "noise"});
		private final JTextField[] relief_values = new JTextField[relief_fields.length];
		private final javax.swing.JCheckBox terrain_box = new javax.swing.JCheckBox("heights: mountains, temperature, gas drift");
		private final JTextField[] terrain_values = new JTextField[terrain_fields.length];

		public NewWorldPanel(WorldBio world, Runnable close) {
			this(initial(world), creator(world), close);
		}
		public NewWorldPanel(WorldSettings initial, java.util.function.Consumer<WorldSettings> create, Runnable close) {
			super(new BorderLayout(6, 6));
			this.create = create;
			this.close = close;
			this.s = initial.copy();
			setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("World & species", world_tab());
			tabs.addTab("Oxygen bots", bots_tab());
			tabs.addTab("Plants (biosim)", plants_tab());
			tabs.addTab("Neuro", neuro_tab());
			tabs.addTab("Flies", flies_tab());
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
			//перезапуск при вымирании и перенос лучших (Constant.restart_mode, elite..., Elite)
			JPanel run = new JPanel(new FlowLayout(FlowLayout.LEFT));
			run.add(new JLabel("restart when extinct"));
			restart_box.setToolTipText("off — no; all — when every population died out; any — when any population died out");
			run.add(restart_box);
			run.add(new JLabel("   "));
			forced_box.setToolTipText("restart even if nobody died out: after this many steps of the current try; the best are carried as below");
			forced_box.addActionListener(e -> field_restart_steps.setEnabled(forced_box.isSelected()));
			run.add(forced_box);
			run.add(field_restart_steps);
			run.add(new JLabel("steps   "));
			elite_box.setToolTipText("on restart, the best of the series (by the scores below) are cloned exactly to fill each population");
			run.add(elite_box);
			run.add(new JLabel("keep"));
			run.add(field_elite_size);
			run.add(new JLabel("best by:"));
			for (int i = 0; i < elite_fields.length; i++) {
				elite_fields[i] = new javax.swing.JCheckBox(Elite.FIELDS[i]);
				run.add(elite_fields[i]);
			}
			run.add(new JLabel("(each divided by its mean, summed)"));
			//режим высот (Constant.terrain..., Terrain): галочка и числа
			JPanel heights = new JPanel(new FlowLayout(FlowLayout.LEFT));
			terrain_box.setToolTipText(ParamDocs.constant.get("terrain"));
			terrain_box.addActionListener(e -> terrain_changed());
			heights.add(terrain_box);
			String[] labels = {"   temperature °C: low", "mid", "high", "   photosynthesis °C: min", "opt", "max", "   gas drift"};
			for (int i = 0; i < terrain_fields.length; i++) {
				terrain_values[i] = new JTextField(4);
				terrain_values[i].setToolTipText(ParamDocs.constant.get(terrain_fields[i]));
				heights.add(new JLabel(labels[i]));
				heights.add(terrain_values[i]);
			}
			JPanel relief = new JPanel(new FlowLayout(FlowLayout.LEFT));
			relief.add(new JLabel("relief"));
			relief_box.setToolTipText(ParamDocs.constant.get("terrain_relief"));
			relief_box.addActionListener(e -> relief_changed());
			relief.add(relief_box);
			String[] relief_labels = {"   height m: min", "max", "   mountains share", "crest sharpness", "mountain waves across"};
			for (int i = 0; i < relief_fields.length; i++) {
				relief_values[i] = new JTextField(i < 2 ? 5 : 4);
				relief_values[i].setToolTipText(ParamDocs.constant.get(relief_fields[i]));
				relief.add(new JLabel(relief_labels[i]));
				relief.add(relief_values[i]);
			}
			JPanel north = new JPanel(new java.awt.GridLayout(4, 1));
			north.add(top);
			north.add(run);
			north.add(heights);
			north.add(relief);
			p.add(north, BorderLayout.NORTH);
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
		private JPanel flies_tab() {//мозг мухи на видеокарте: популяция с мозгом fly (sct.neuro.FlyBrain)
			JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 2));
			rows.setBorder(BorderFactory.createTitledBorder("Fly brain (populations with brain \"fly\"; sensors and actions — flybrain/mapping.yaml)"));
			JPanel mode = new JPanel(new FlowLayout(FlowLayout.LEFT));
			mode.add(new JLabel("mode"));
			mode.add(fly_mode_box);
			mode.add(fly_mode_hint);
			fly_mode_box.addActionListener(e -> fly_mode_changed());
			rows.add(mode);
			JPanel slots = new JPanel(new FlowLayout(FlowLayout.LEFT));
			slots.add(new JLabel("GPU slots (max flies alive)"));
			field_fly_slots.setToolTipText("learn mode only; the recommended number is printed by the fly service at start");
			slots.add(field_fly_slots);
			slots.add(new JLabel("— the recommended number is printed by the fly service at start; more flies — slower world"));
			rows.add(slots);
			JPanel diet = new JPanel(new FlowLayout(FlowLayout.LEFT));
			diet.add(new JLabel("share of organic eaters (0..1)"));
			field_fly_recyclers.setToolTipText("a new fly gets its diet: organics (recycle) or photosynthesis (photo), never both");
			diet.add(field_fly_recyclers);
			diet.add(new JLabel("   CO2 exhaled per step (share of O2 used)"));
			diet.add(field_fly_move_co2);
			rows.add(diet);
			JPanel conn = new JPanel(new FlowLayout(FlowLayout.LEFT));
			conn.add(new JLabel("service host (auto — this machine)"));
			conn.add(field_fly_host);
			conn.add(new JLabel("port"));
			conn.add(field_fly_port);
			conn.add(new JLabel("python"));
			conn.add(field_fly_python);
			rows.add(conn);
			JPanel p = new JPanel(new BorderLayout(6, 6));
			p.add(rows, BorderLayout.NORTH);
			p.add(new JLabel("  Add flies on the first tab: a population with body OXYGEN_BOT and brain \"fly\"."), BorderLayout.CENTER);
			return p;
		}
		private static String constant_value(String name) {//текущее значение поля Constant по имени
			try {
				return WorldSettings.field_value(Constant.class.getField(name), null);
			}catch (NoSuchFieldException e) {
				return "";
			}
		}
		private void relief_changed() {//доля, острота и размер гор — только у peaks; метры — у любого рельефа
			boolean peaks = "peaks".equals(relief_box.getSelectedItem());
			for (int i = 2; i < relief_values.length; i++) {
				relief_values[i].setEnabled(peaks);
			}
		}
		private void terrain_changed() {//числа режима высот — только когда он включен
			for (JTextField f : terrain_values) {
				f.setEnabled(terrain_box.isSelected());
			}
		}
		private void fly_mode_changed() {
			boolean learn = "learn".equals(fly_mode_box.getSelectedItem());
			fly_mode_hint.setText(learn ? "flies multiply while a GPU slot is free" : "flies do not multiply; one GPU slot per fly of the population");
			field_fly_slots.setEnabled(learn);
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
			JPanel p = new JPanel(new BorderLayout(6, 6));
			JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
			top.add(new JLabel("population with brain 'neuro'"));
			top.add(neuro_pop_box);
			top.add(new JLabel("   actions — on the tab of its body (Oxygen bots / Plants)"));
			p.add(top, BorderLayout.NORTH);
			JPanel center = new JPanel(new BorderLayout());
			center.setBorder(BorderFactory.createTitledBorder("Network: hidden layers, activation, memory, choice, mutation, kin"));
			center.add(neuro_table, BorderLayout.CENTER);
			p.add(center, BorderLayout.CENTER);
			JPanel right = new JPanel(new BorderLayout());
			right.setBorder(BorderFactory.createTitledBorder("Sensors (names for sensors_off)"));
			neuro_sensors.setEditable(false);
			right.add(new JScrollPane(neuro_sensors), BorderLayout.CENTER);
			right.setPreferredSize(new Dimension(300, 100));
			p.add(right, BorderLayout.EAST);
			neuro_pop_box.addActionListener(e -> {
				store_neuro_params();
				show_neuro_params((Population) neuro_pop_box.getSelectedItem());
			});
			return p;
		}
		private void show_neuro_params(Population pop) {
			neuro_shown = pop;
			List<String[]> rows = new ArrayList<String[]>();
			StringBuilder sensors = new StringBuilder();
			if (pop != null) {
				for (Field f : sct.neuro.NeuroConfig.class.getFields()) {
					if (!Modifier.isStatic(f.getModifiers())) {
						rows.add(new String[]{f.getName(), WorldSettings.field_value(f, pop.neuro_config())});
					}
				}
				String[] names = pop.species == Species.PLANT ? sct.neuro.PlantBody.instance.inputs() : sct.neuro.BotBody.instance.inputs();
				for (String n : names) {
					sensors.append(n).append(System.lineSeparator());
				}
			}
			neuro_table.set_rows(rows);
			neuro_sensors.setText(sensors.toString());
		}
		private void store_neuro_params() {
			if (neuro_shown != null) {
				for (Map.Entry<String, String> e : neuro_table.values().entrySet()) {
					neuro_shown.neuro_config().set(e.getKey(), e.getValue());
				}
			}
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
			restart_box.setSelectedItem(s.constants.getOrDefault("restart_mode", Constant.restart_mode));
			forced_box.setSelected(!"0".equals(s.constants.getOrDefault("restart_forced", String.valueOf(Constant.restart_forced))));
			field_restart_steps.setText(s.constants.getOrDefault("restart_steps", String.valueOf(Constant.restart_steps)));
			field_restart_steps.setEnabled(forced_box.isSelected());
			elite_box.setSelected(!"0".equals(s.constants.getOrDefault("elite", String.valueOf(Constant.elite))));
			field_elite_size.setText(s.constants.getOrDefault("elite_size", String.valueOf(Constant.elite_size)));
			java.util.Set<String> ef = new java.util.HashSet<String>(java.util.Arrays.asList(s.constants.getOrDefault("elite_fields", Constant.elite_fields).split(",")));
			for (int i = 0; i < elite_fields.length; i++) {
				elite_fields[i].setSelected(ef.contains(Elite.FIELDS[i]));
			}
			fly_mode_box.setSelectedItem(s.constants.getOrDefault("fly_mode", Constant.fly_mode));
			field_fly_slots.setText(s.constants.getOrDefault("fly_slots", String.valueOf(Constant.fly_slots)));
			field_fly_recyclers.setText(s.constants.getOrDefault("fly_recyclers", String.valueOf(Constant.fly_recyclers)));
			field_fly_move_co2.setText(s.constants.getOrDefault("fly_move_co2", String.valueOf(Constant.fly_move_co2)));
			field_fly_host.setText(s.constants.getOrDefault("fly_host", Constant.fly_host));
			field_fly_port.setText(s.constants.getOrDefault("fly_port", String.valueOf(Constant.fly_port)));
			field_fly_python.setText(s.constants.getOrDefault("fly_python", Constant.fly_python));
			fly_mode_changed();
			terrain_box.setSelected(!"0".equals(s.constants.getOrDefault("terrain", String.valueOf(Constant.terrain))));
			for (int i = 0; i < terrain_fields.length; i++) {
				terrain_values[i].setText(s.constants.getOrDefault(terrain_fields[i], constant_value(terrain_fields[i])));
			}
			terrain_changed();
			relief_box.setSelectedItem(s.constants.getOrDefault("terrain_relief", Constant.terrain_relief));
			for (int i = 0; i < relief_fields.length; i++) {
				relief_values[i].setText(s.constants.getOrDefault(relief_fields[i], constant_value(relief_fields[i])));
			}
			relief_changed();
			List<String[]> rows = new ArrayList<String[]>();
			for (Map.Entry<String, String> e : s.constants.entrySet()) {
				if (!e.getKey().equals("world_scale") && !by_populations.contains(e.getKey()) && !world_keys.contains(e.getKey())
					&& !fly_keys.contains(e.getKey()) && !terrain_keys.contains(e.getKey())) {//размер, перезапуск, высоты — на первой вкладке, мухи — на своей, остальное — у популяций
					rows.add(new String[]{e.getKey(), e.getValue()});
				}
			}
			constants_table.set_rows(rows);
			plant_shown = null;
			neuro_shown = null;
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
			store_neuro_params();
			plant_shown = null;
			neuro_shown = null;
			fill_box(bot_pop_box, Species.OXYGEN_BOT);
			fill_box(plant_pop_box, Species.PLANT);
			Object neuro_selected = neuro_pop_box.getSelectedItem();
			neuro_pop_box.removeAllItems();
			for (Population p : s.populations) {
				if (p.brain.equals(Brains.NEURO)) {
					neuro_pop_box.addItem(p);
				}
			}
			if (neuro_selected != null && s.populations.contains(neuro_selected)) {
				neuro_pop_box.setSelectedItem(neuro_selected);
			}
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
			s.constants.put("restart_mode", (String) restart_box.getSelectedItem());
			s.constants.put("restart_forced", forced_box.isSelected() ? "1" : "0");
			int every;
			try {
				every = Integer.parseInt(field_restart_steps.getText().trim());
			}catch (NumberFormatException e) {
				every = 0;
			}
			if (every < 1) {
				throw new IllegalArgumentException("принудительный перезапуск — через целое число шагов, не меньше 1");
			}
			s.constants.put("restart_steps", String.valueOf(every));
			s.constants.put("elite", elite_box.isSelected() ? "1" : "0");
			int keep = Integer.parseInt(field_elite_size.getText().trim());
			if (keep < 1) {
				throw new IllegalArgumentException("сколько лучших хранить — не меньше 1");
			}
			s.constants.put("elite_size", String.valueOf(keep));
			StringBuilder ef = new StringBuilder();
			for (int i = 0; i < elite_fields.length; i++) {
				if (elite_fields[i].isSelected()) {
					ef.append(ef.length() == 0 ? "" : ",").append(Elite.FIELDS[i]);
				}
			}
			if (elite_box.isSelected() && ef.length() == 0) {
				throw new IllegalArgumentException("для переноса лучших отметьте хотя бы одно поле оценки");
			}
			s.constants.put("elite_fields", ef.toString());
			s.constants.put("terrain", terrain_box.isSelected() ? "1" : "0");
			for (int i = 0; i < terrain_fields.length; i++) {
				String v = terrain_values[i].getText().trim();
				try {
					Double.parseDouble(v);
				}catch (NumberFormatException e) {
					throw new IllegalArgumentException("режим высот: " + terrain_fields[i] + " — нужно число, а не «" + v + "»");
				}
				s.constants.put(terrain_fields[i], v);
			}
			s.constants.put("terrain_relief", (String) relief_box.getSelectedItem());
			for (int i = 0; i < relief_fields.length; i++) {
				String v = relief_values[i].getText().trim();
				try {
					if (relief_fields[i].equals("terrain_size")) {
						if (Integer.parseInt(v) < 1) {
							throw new IllegalArgumentException("рельеф: волн гор по ширине мира — не меньше 1");
						}
					}else {
						Double.parseDouble(v);
					}
				}catch (NumberFormatException e) {
					throw new IllegalArgumentException("рельеф: " + relief_fields[i] + " — нужно число, а не «" + v + "»");
				}
				s.constants.put(relief_fields[i], v);
			}
			s.constants.put("fly_mode", (String) fly_mode_box.getSelectedItem());
			int slots = Integer.parseInt(field_fly_slots.getText().trim());
			if (slots < 1) {
				throw new IllegalArgumentException("слотов мух на видеокарте — не меньше 1");
			}
			s.constants.put("fly_slots", String.valueOf(slots));
			double rec = Double.parseDouble(field_fly_recyclers.getText().trim());
			if (rec < 0 || rec > 1) {
				throw new IllegalArgumentException("доля мух-едоков органики — от 0 до 1");
			}
			s.constants.put("fly_recyclers", String.valueOf(rec));
			double co2 = Double.parseDouble(field_fly_move_co2.getText().trim());
			if (co2 < 0) {
				throw new IllegalArgumentException("CO2 на шаг мухи — не меньше 0");
			}
			s.constants.put("fly_move_co2", String.valueOf(co2));
			s.constants.put("fly_host", field_fly_host.getText().trim().isEmpty() ? "auto" : field_fly_host.getText().trim());
			s.constants.put("fly_port", String.valueOf(Integer.parseInt(field_fly_port.getText().trim())));
			s.constants.put("fly_python", field_fly_python.getText().trim());
			s.seed = Long.parseLong(field_seed.getText().trim());
			store_plant_params();
			store_neuro_params();
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
		static WorldSettings initial(WorldBio world) {//настройки прошлого мира или запуска
			return world.last_settings != null ? world.last_settings : WorldSettings.startup != null ? WorldSettings.startup : WorldSettings.capture();
		}
		static java.util.function.Consumer<WorldSettings> creator(WorldBio world) {//создать мир в этом окне
			return s -> {
				s.copy().apply(world);
				world.last_settings = s.copy();//запоминаются только настройки, с которыми мир создался
			};
		}
		private void create() {
			try {
				store_ui();
				String warning = s.check();
				if (warning != null && JOptionPane.showConfirmDialog(this, warning + System.lineSeparator() + System.lineSeparator() + "Создать мир все равно?", "Проверка настроек",
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
					return;
				}
				create.accept(s.copy());
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
				for (String b : p.species == Species.PLANT ? Brains.plant_brains : Brains.bot_brain_names()) {
					box.addItem(b);
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
