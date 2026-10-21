package sct;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class WorldBio extends JPanel implements MouseWheelListener, MouseListener, MouseMotionListener {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	ArrayList<IBot> objects;//все существа мира
	IBot[][] Map = new IBot[Constant.world_scale[0]][Constant.world_scale[1]];//общая сетка: в клетке любое существо
	Random rand = new Random();
	Timer timer;//перерисовка окна (~30 кадров/с); сама симуляция идет в sim_thread
	double[][] oxygen_map;//кислород
	double[][] co2_map;//углекислота
	public double[][] org_map;//органика (общая: боты Oxygen и растения)
	public sct.plants.PlantWorld plants;//растения: общие данные мира для них (первая популяция растений; иначе null)
	public java.util.List<Population> populations = new java.util.ArrayList<Population>();//популяции текущего мира
	public java.util.List<Population> next_populations;//популяции для следующего «New world» (диалог); null — из параметров
	public Interaction next_interaction;//таблица взаимодействий для следующего мира (диалог); null — по умолчанию
	public WorldSettings last_settings;//настройки, с которыми диалог «Новый мир» создал мир (null — еще не открывали)
	int plant_count = 0;//клеток растений (с трупами)
	double[][] mnr_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];//минералы; когда в мире растения — их почва (энергия почвы biosim)
	int[][] height_map = new int[Constant.world_scale[0]][Constant.world_scale[1]];//карта высот
	private double zoomFactor = 1;
	private double prevZoomFactor = 1;
	private boolean zoomer;
	private boolean dragger;
	private boolean released;
	private double xOffset = 0;
	private double yOffset = 0;
	private int xDiff;
	private int yDiff;
	private Point startPoint;
	protected boolean render = true;
	protected boolean render_border = false;
	protected volatile boolean pause = false;
	// параметры
	int steps = 0;//количество шагов симуляции
	int b_count = 0;//количество ботов
	int obj_count = 0;//количество объектов
	int org_count = 0;//количество семян
	int population = 0;//номер популяции (сколько раз создавали мир)
	public volatile long started = System.currentTimeMillis();//когда мир создан (реальное время); сохраняется в файле мира
	//перезапуск при вымирании (Constant.restart_mode) и перенос лучших (Constant.elite, Elite)
	public volatile int restarts = 0;//перезапусков в этой серии; New world / Again — с нуля
	public volatile long series_started = started;//когда началась серия (первый мир до перезапусков)
	java.util.List<Elite> elites;//зал славы по номеру популяции; null — перенос выключен
	private boolean restarting = false;
	private java.util.List<java.util.List<Object>> carry;//наследственность лучших для заселения (на время newPopulation)
	volatile IBot selection = null;//выбранное существо (бот, растение, нейро...)
	volatile IBot prev_selection = null;//предыдущее выбранное — для сравнения геномов
	int draw_type = 0;//режим отрисовки
	int ground_layer = 0;//фон окна: слой земли (Constant.ground_layer_names)
	int gas_layer = 0;//и слой газа поверх него (Constant.gas_layer_names) — включаются независимо
	//
	//поток симуляции
	//Все изменения мира и его чтение для отрисовки — только под lock.
	//Справедливая блокировка: поток окна не ждет дольше одного шага симуляции.
	final ReentrantLock lock = new ReentrantLock(true);
	public static final int[] speed_levels = {1, 2, 5, 10, 20, 50, 0};//шагов за кадр, 0 — без ограничения
	volatile int speed_idx = speed_levels.length - 1;
	private volatile boolean step_once = false;
	private static final long frame_ns = 33_000_000L;
	volatile double ms_per_step = 0;//скорость, для панели
	private final PixelRenderer renderer = new PixelRenderer();


	public WorldBio() {
		this(true);
	}
	public WorldBio(boolean start_threads) {//false — мир без окна и потоков (замеры, тесты)
		setLayout(null);
		objects = new ArrayList<IBot>();
		setBackground(new Color(Theme.world_bg));
		initComponent();
		Presets.apply_seed(this);//до создания популяции: одно зерно — один и тот же мир

		newPopulation();

		timer = new Timer(33, e -> repaint());
		if (start_threads) {
			timer.start();
			start_simulation();
		}
	}
	public void start_simulation() {//поток симуляции; без окна (сервер) — без таймера перерисовки
		Thread sim_thread = new Thread(this::simulation_loop, "simulation");
		sim_thread.setDaemon(true);
		sim_thread.start();
	}

	private void initComponent() {
		addMouseWheelListener(this);
		addMouseMotionListener(this);
		addMouseListener(this);
	}

	//
	//ПОТОК СИМУЛЯЦИИ
	//
	private void simulation_loop() {
		while (true) {
			long frame_start = System.nanoTime();
			int n;
			if (pause) {
				n = step_once ? 1 : 0;
				step_once = false;
			}else {
				n = speed_levels[speed_idx];
			}
			if (n == 0 && !pause) {//без ограничения: шаги подряд до конца кадра
				while (!pause && System.nanoTime() - frame_start < frame_ns) {
					locked_step();
				}
				continue;
			}
			for (int i = 0; i < n; i++) {
				locked_step();
			}
			long rest = frame_ns - (System.nanoTime() - frame_start);
			if (rest > 0) {
				try {
					Thread.sleep(rest / 1_000_000L, (int)(rest % 1_000_000L));
				}catch (InterruptedException e) {
					return;
				}
			}
		}
	}
	private void locked_step() {
		lock.lock();
		try {
			long t0 = System.nanoTime();
			step();
			ms_per_step = ms_per_step * 0.9 + (System.nanoTime() - t0) / 1e6 * 0.1;
		}finally {
			lock.unlock();
		}
	}
	public void request_step() {//один шаг на паузе
		step_once = true;
	}
	public void faster() {
		speed_idx = Math.min(speed_levels.length - 1, speed_idx + 1);
	}
	public void slower() {
		speed_idx = Math.max(0, speed_idx - 1);
	}
	public static String brain_name(IBot o) {//имя мозга существа: genome, simple, neuro, think_new...
		if (o instanceof Bot b) {
			return b.brain == null ? "genome" : b.brain.name();
		}
		if (o instanceof sct.plants.Plant p) {
			return p.brain == null ? "-" : p.brain.name();
		}
		return "-";
	}
	public String speed_name() {
		int n = speed_levels[speed_idx];
		return n == 0 ? "max" : n + " steps/frame";
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		boolean draggerOld = dragger;

		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(new Color(Theme.world_bg));
		g2.fillRect(0, 0, getWidth(), getHeight());
		int width = Constant.world_scale[0] * Constant.size;
		int height = Constant.world_scale[1] * Constant.size;

		BufferedImage buff4;
		lock.lock();//мир не меняется, пока снимаем кадр
		try {
			buff4 = renderer.render(this);
		}finally {
			lock.unlock();
		}
		Graphics2D g2dTail = buff4.createGraphics();

		AffineTransform at = new AffineTransform();

		if (zoomer) {
			double xRel = MouseInfo.getPointerInfo().getLocation().getX() - getLocationOnScreen().getX();
			double yRel = MouseInfo.getPointerInfo().getLocation().getY() - getLocationOnScreen().getY();

			double zoomDiv = zoomFactor / prevZoomFactor;

			xOffset = (zoomDiv) * (xOffset) + (1 - zoomDiv) * xRel;
			yOffset = (zoomDiv) * (yOffset) + (1 - zoomDiv) * yRel;

			at.translate(xOffset, yOffset);
			at.scale(zoomFactor, zoomFactor);
			prevZoomFactor = zoomFactor;

			g2.transform(at);
			zoomer = false;
		}else {
			if (dragger) {
				at.translate(xOffset + xDiff, yOffset + yDiff);
				at.scale(zoomFactor, zoomFactor);
				g2.transform(at);
				if (released) {
					xOffset += xDiff;
					yOffset += yDiff;
					dragger = false;
				}
			}else {
				at.translate(xOffset, yOffset);
				at.scale(zoomFactor, zoomFactor);
				g2.transform(at);
			}
		}

	    if (render_border) {
			g2dTail.setColor(new Color(50, 250, 50));
			g2dTail.fillRect(0, 0, 100, 100);
			g2dTail.drawRect(0, 0, width, height);
	    }
	    g2dTail.dispose();

		// Далее множим тайл для создания иллюзии бесконечного мира
	    // Размеры тайла
	    int tileWidth = width;
	    int tileHeight = height;

	    // Размеры панели
	    Dimension panelSize = getSize();

	    double xOffsetTile;
	    double yOffsetTile;

		if (draggerOld) {
			xOffsetTile = xOffset + xDiff;
			yOffsetTile = yOffset + yDiff;
		}else {
		    xOffsetTile = xOffset;
		    yOffsetTile = yOffset;
		}

	    // Определение видимой области в мировых координатах
	    int visibleXStart = (int) (-(xOffsetTile) / zoomFactor - tileWidth);
	    int visibleYStart = (int) (-(yOffsetTile) / zoomFactor - tileHeight);
	    int visibleXEnd = visibleXStart + (int) (panelSize.width / zoomFactor + tileWidth*2);
	    int visibleYEnd = visibleYStart + (int) (panelSize.height / zoomFactor + tileHeight*2);

	    // Отрисовка тайлов в видимой области
	    for (int x = (visibleXStart / tileWidth) * tileWidth; x < visibleXEnd; x += tileWidth) {
	        for (int y = (visibleYStart / tileHeight) * tileHeight; y < visibleYEnd; y += tileHeight) {
	            g2.drawImage(buff4, x, y, tileWidth, tileHeight, null);
	        }
	    }

	    //------------------------------------------
	    // Отрисовка элементов поверх тайлов без повторения
	    if (render_border) {
			g2.setColor(new Color(50, 0, 250));
			g2.setStroke(new BasicStroke(3));
			g2.drawRect(0, 0, width, height);
			g2.setStroke(new BasicStroke(1));
			g.setColor(new Color(200, 100, 10));
			g.fillRect(-50, -50, 100, 100);
			g.setColor(new Color(50, 50, 200));
			g2.drawRect(0, 0, 100, 100);
			paintCoordGrid(g2);
	    }
	}

	public void paintCoordGrid(Graphics g) {
		g.drawLine(0, 0, 300, 0);
		g.drawLine(0, 0, 0, 300);
		g.drawLine(0, 0, 0, -300);
		g.drawLine(0, 0, -300, 0);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		zoomer = true;
		// Zoom in
		if (e.getWheelRotation() < 0) {
			zoomFactor *= 1.1;
		}else {
			zoomFactor /= 1.1;
		}
	    // Ограничение масштаба
	    zoomFactor = Math.max(0.1, Math.min(10, zoomFactor));

		repaint();

	}

	@Override
	public void mouseDragged(MouseEvent e) {
		Point curPoint = e.getLocationOnScreen();
		xDiff = curPoint.x - startPoint.x;
		yDiff = curPoint.y - startPoint.y;

		dragger = true;
		repaint();

	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	public int[] world_cell(MouseEvent e) {//клетка мира под курсором
		int xLocal = Constant.mod((int)Math.floor((e.getX() - xOffset) / zoomFactor / Constant.size), Constant.world_scale[0]);
		int yLocal = Constant.mod((int)Math.floor((e.getY() - yOffset) / zoomFactor / Constant.size), Constant.world_scale[1]);
		return new int[]{xLocal, yLocal};
	}

	@Override
	public void mouseClicked(MouseEvent e) {//ЛКМ — выбрать существо, ПКМ — текущий инструмент
		int[] c = world_cell(e);
		lock.lock();
		try {
			IBot o = Map[c[0]][c[1]];
			if (o != null && !o.alive()) {
				o = null;
			}
			if (SwingUtilities.isRightMouseButton(e)) {
				Tools.all[tool_idx].apply(this, o, c[0], c[1]);
			}else {
				select(o);
			}
		}finally {
			lock.unlock();
		}
		repaint();
	}
	//
	//инструмент ПКМ
	volatile int tool_idx = 0;
	public void next_tool(int delta) {
		tool_idx = Math.floorMod(tool_idx + delta, Tools.all.length);
	}
	public void reset_tool() {//Esc: инструмент «Select», выделение снято
		tool_idx = 0;
		selection = null;
		prev_selection = null;
	}
	public void select(IBot b) {
		if (b != selection) {
			if (selection != null) {
				prev_selection = selection;
			}
			selection = b;
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		released = false;
		startPoint = MouseInfo.getPointerInfo().getLocation();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		released = true;
		repaint();

	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	public void newPopulation() {//создать случайную популяцию
		lock.lock();
		try {
			populations = next_populations != null ? next_populations : Population.defaults();
			for (int i = 0; i < populations.size(); i++) {
				populations.get(i).id = i;
			}
			Interaction.current = next_interaction != null ? next_interaction : new Interaction(populations.size());
			kill_all();
			started = System.currentTimeMillis();
			if (!restarting) {//новый мир по кнопке — новая серия: счет перезапусков и зал славы с нуля
				restarts = 0;
				series_started = started;
				elites = null;
			}
			for (Population p : populations) {
				if (p.species == Species.OXYGEN_BOT) {
					spawn_bots(p);
				}
			}
			spawn_plants();
			Ledger.reset(this);
			History.clear();
			History.name_populations(populations);
			KinClans.update(this);
		}finally {
			lock.unlock();
		}
		repaint();
	}
	private void spawn_bots(Population p) {//боты популяции p — как раньше newPopulation
		Brain<Bot> brain = Brains.make_bot_brain(p);
		p.bot_brain = brain;
		{
			for (int i = 0; i < p.count; i++) {
				while(true){
					int x = rand.nextInt(Constant.world_scale[0]);
					int y = rand.nextInt(Constant.world_scale[1]);
					if (Map[x][y] == null) {
						Bot new_bot = new Bot(
							x,
							y,
							new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256)),
							1000,
							0,
							oxygen_map,
							co2_map,
							org_map,
							Map,
							objects
						);
						new_bot.brain = brain == null ? null : brain.spawn();//нейросеть — своя у каждого
						new_bot.pop = p;
						Object h = heritage_for(p, i);
						if (h != null) {
							new_bot.take_heritage(h);
						}
						objects.add(new_bot);
						Map[x][y] = new_bot;
						break;
					}
				}
			}
		}
	}
	private void spawn_plants() {//популяции растений — как init_world в biosim
		plants = null;
		boolean any_bots = false;
		for (Population p : populations) {
			if (p.species == Species.OXYGEN_BOT && p.count > 0) {
				any_bots = true;
			}
		}
		for (Population p : populations) {
			if (p.species != Species.PLANT || p.count <= 0) {
				continue;
			}
			sct.plants.PlantConfig c = p.plant_config;
			if (plants == null) {//первая популяция растений задает почву мира и минерализацию
				for (int x = 0; x < Constant.world_scale[0]; x++) {//минералы — почва растений: органика минерализуется в них, корни их забирают
					for (int y = 0; y < Constant.world_scale[1]; y++) {
						mnr_map[x][y] = c.energy_around_start_value;
						if (!any_bots) {//мир без ботов Oxygen — стартовая органика biosim
							org_map[x][y] = c.organic_around_start_value;
						}
					}
				}
			}
			sct.plants.PlantWorld pw = new sct.plants.PlantWorld(Map, org_map, mnr_map, co2_map, oxygen_map, objects, c);
			pw.pop = p;
			p.plant_world = pw;
			if (plants == null) {
				plants = pw;
			}
			Brain<sct.plants.Plant> brain = Brains.make_plant_brain(p);
			p.plant_brain = brain;
			int created = 0;
			int tries = 0;
			java.util.Random r = sct.plants.PlantWorld.rand;
			while (created < p.count && tries < 1000000) {
				tries++;
				int x = r.nextInt(Constant.world_scale[0]);
				int y = r.nextInt(Constant.world_scale[1]);
				if (Map[x][y] != null) {
					continue;
				}
				sct.plants.Plant plant = sct.plants.Plant.seedling(pw, x, y, brain.spawn());
				Object h = heritage_for(p, created);
				if (h != null) {
					plant.take_heritage(h);
				}
				objects.add(plant);
				Map[x][y] = plant;
				created++;
			}
		}
	}
	public void kill_all() {//очистить мир
		steps = 0;
		population++;
		selection = null;
		prev_selection = null;
		objects = new ArrayList<IBot>();
		int noise_seed = rand.nextInt(-1000000000, 1000000000);//одно число из rand мира при любом рельефе
		SimplexNoise noise = new SimplexNoise(noise_seed);//шум
		boolean peaks = Constant.terrain_relief.equals("peaks");
		Map = new IBot[Constant.world_scale[0]][Constant.world_scale[1]];
		oxygen_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		co2_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		org_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		mnr_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		height_map = new int[Constant.world_scale[0]][Constant.world_scale[1]];//размер мира мог поменяться (диалог «Новый мир»)
		//стартовые ресурсы
		for (int x = 0; x < Constant.world_scale[0]; x++) {
			for (int y = 0; y < Constant.world_scale[1]; y++) {
				oxygen_map[x][y] = Constant.starting_ox;
				co2_map[x][y] = Constant.starting_co2;
				org_map[x][y] = Constant.starting_org;
				mnr_map[x][y] = 0;
				Map[x][y] = null;
				if (!peaks) {
					height_map[x][y] = seamless_height(noise, x, y);
				}
			}
		}
		if (peaks) {//горы как в IndustrialGate (Relief)
			height_map = Relief.generate(Constant.world_scale[0], Constant.world_scale[1], noise_seed);
		}
	}
	//высота 0..1000 без шва по краям замкнутого мира: смесь четырех копий шума, сдвинутых на ширину
	//и высоту мира, с весами по положению клетки (у края x=0 и x=W — одни и те же копии).
	//Смесь независимых шумов дает меньший разброс — он восстанавливается делением на sqrt(сумма весов^2)
	private static int seamless_height(SimplexNoise noise, int x, int y) {
		int w = Constant.world_scale[0], h = Constant.world_scale[1];
		double u = (double) x / w, v = (double) y / h;
		double[] wt = {(1 - u) * (1 - v), u * (1 - v), (1 - u) * v, u * v};
		double[] n = {
			sample(noise, x, y), sample(noise, x - w, y), sample(noise, x, y - h), sample(noise, x - w, y - h)};
		double s = 0, s2 = 0;
		for (int i = 0; i < 4; i++) {
			s += wt[i] * n[i];
			s2 += wt[i] * wt[i];
		}
		return (int) Math.max(0, Math.min(1000, 500 + s / Math.sqrt(s2)));
	}
	private static double sample(SimplexNoise noise, int x, int y) {//шум около 0: -500..500
		return noise.sumOctaves(8, x, y, 0.5F, 0.007F, 0, 1000) - 500;
	}

	//
	//ШАГ СИМУЛЯЦИИ (вызывается под lock)
	//
	public void step() {
		Ledger.begin_step();
		Terrain.update(this);//высоты, температура, эффективность фотосинтеза (пересчет — при новой карте или параметрах)
		steps++;
		b_count = 0;
		obj_count = 0;
		org_count = 0;
		plant_count = 0;
		//
		int[] live = new int[populations.size()];//живых по популяциям — для проверки вымирания
		int live_other = 0;
		ListIterator<IBot> bot_iterator = objects.listIterator();
		while (bot_iterator.hasNext()) {
			IBot next_bot = bot_iterator.next();
			next_bot.Update(bot_iterator);
			if (next_bot.living()) {
				Population lp = next_bot.population();
				if (lp != null && lp.id < live.length) {
					live[lp.id]++;
				}else {
					live_other++;
				}
			}
			obj_count++;
			if (next_bot instanceof Bot) {
				if (((Bot) next_bot).state != 0) {
					org_count++;
				}else {
					b_count++;
				}
			}else {
				plant_count++;
			}
		}
		//
		if (plants != null) {//органика -> энергия почвы, вымывание почвы (конец тика biosim)
			sct.plants.Plant.mineralize(plants);
		}
		WorldUtils.gas(oxygen_map, org_map, -1);//режим высот: кислород легче — растекается больше вверх,
		WorldUtils.gas(co2_map, org_map, 1);//CO2 тяжелее — вниз; без режима — поровну, как раньше
		boolean influx = Constant.mineral_influx > 0 || (Constant.mineral_influx < 0 && plants == null);
		if (influx) {
			if (plants != null) {//минералы — энергия растений: приток (и срез до 1000) — статья журнала
				double before = Ledger.sum(mnr_map);
				WorldUtils.minerals(mnr_map);
				Ledger.in_mineral_influx += Ledger.sum(mnr_map) - before;
			}else {
				WorldUtils.minerals(mnr_map);
			}
		}
		//
		objects.removeIf(b -> {//один проход вместо удаления по одному из середины списка
			if (b.alive()) {
				return false;
			}
			offer_elite(b);//жизнь закончена — в зал славы, если лучше худшего там
			return true;
		});
		if (selection != null && !selection.alive()) {
			selection = null;
		}
		Ledger.end_step(this);
		if (steps % KinClans.update_every == 0) {
			KinClans.update(this);
		}
		History.record(this, ms_per_step);
		if (extinct(live, live_other) || forced_restart_due()) {
			auto_restart();
		}
	}
	private boolean forced_restart_due() {//принудительный перезапуск: попытка прожила restart_steps шагов
		return Constant.restart_forced != 0 && Constant.restart_steps > 0 && steps >= Constant.restart_steps;
	}

	//
	//ПЕРЕЗАПУСК ПРИ ВЫМИРАНИИ И ПЕРЕНОС ЛУЧШИХ
	//
	private boolean extinct(int[] live, int live_other) {//по Constant.restart_mode; популяции без стартовой численности не в счет
		String mode = Constant.restart_mode;
		if (mode.equals("off") || steps < 2) {
			return false;
		}
		boolean any_dead = false, all_dead = live_other == 0, any_started = false;
		for (Population p : populations) {
			if (p.count <= 0 || p.id >= live.length) {
				continue;
			}
			any_started = true;
			if (live[p.id] == 0) {
				any_dead = true;
			}else {
				all_dead = false;
			}
		}
		return any_started && (mode.equals("any") ? any_dead : all_dead);
	}
	private void offer_elite(IBot o) {
		if (Constant.elite == 0 || !(o instanceof BotSuper b) || b.pop == null) {
			return;
		}
		if (elites == null || elites.size() != populations.size()) {
			elites = new java.util.ArrayList<Elite>();
			for (int k = 0; k < populations.size(); k++) {
				elites.add(new Elite(Constant.elite_size, Constant.elite_fields));
			}
		}
		if (b.pop.id < elites.size()) {
			elites.get(b.pop.id).offer(b);
		}
	}
	private Object heritage_for(Population p, int i) {//наследственность i-го существа популяции при заселении: лучшие по кругу
		if (carry == null || p.id >= carry.size()) {
			return null;
		}
		java.util.List<Object> best = carry.get(p.id);
		return best.isEmpty() ? null : best.get(i % best.size());
	}
	void auto_restart() {//под lock (из step)
		if (Constant.elite != 0) {
			for (IBot o : objects) {//кто еще в мире (живые в режиме any, трупы растений) — тоже в зал славы
				offer_elite(o);
			}
			if (elites != null) {
				carry = new java.util.ArrayList<java.util.List<Object>>();
				for (Elite e : elites) {
					carry.add(e.best());
				}
			}
		}
		restarts++;
		long seed = Presets.seed;
		if (seed >= 0) {//зерно задано — у каждого перезапуска свое, иначе мир повторится и вымрет так же
			Presets.seed = seed + restarts * 7919L;
			Presets.apply_seed(this);
			Presets.seed = seed;
		}
		restarting = true;
		try {
			newPopulation();
		}finally {
			restarting = false;
			carry = null;
		}
	}
	public int elite_count(int pop) {//сколько лучших сейчас в зале славы популяции
		return elites == null || pop >= elites.size() ? 0 : elites.get(pop).count();
	}

}
