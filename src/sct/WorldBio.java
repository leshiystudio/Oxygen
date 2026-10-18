package sct;

import javax.swing.JPanel;
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
	ArrayList<Bot> objects;
	Bot[][] Map = new Bot[Constant.world_scale[0]][Constant.world_scale[1]];
	Random rand = new Random();
	Timer timer;//перерисовка окна (~30 кадров/с); сама симуляция идет в sim_thread
	double[][] oxygen_map;//кислород
	double[][] co2_map;//углекислота
	double[][] org_map;//органика
	double[][] mnr_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];//минералы
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
	volatile Bot selection = null;//выбранный бот
	volatile Bot prev_selection = null;//предыдущий выбранный — для сравнения геномов
	int draw_type = 0;//режим отрисовки
	int res_draw_type = 0;//режим отрисовки фона/ресурсов/окружения
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
		objects = new ArrayList<Bot>();
		setBackground(new Color(Theme.world_bg));
		initComponent();

		newPopulation();

		timer = new Timer(33, e -> repaint());
		if (start_threads) {
			timer.start();
			Thread sim_thread = new Thread(this::simulation_loop, "simulation");
			sim_thread.setDaemon(true);
			sim_thread.start();
		}
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
	public void mouseClicked(MouseEvent e) {
		int[] c = world_cell(e);
		lock.lock();
		try {
			Bot b = Map[c[0]][c[1]];//выбор бота
			select(b != null && b.killed == 0 ? b : null);
		}finally {
			lock.unlock();
		}
		repaint();
	}
	public void select(Bot b) {
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
			kill_all();
			for (int i = 0; i < Constant.starting_bot_count; i++) {
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
						objects.add(new_bot);
						Map[x][y] = new_bot;
						break;
					}
				}
			}
		}finally {
			lock.unlock();
		}
		repaint();
	}
	public void kill_all() {//очистить мир
		steps = 0;
		population++;
		selection = null;
		prev_selection = null;
		objects = new ArrayList<Bot>();
		SimplexNoise noise = new SimplexNoise(rand.nextInt(-1000000000, 1000000000));//шум
		Map = new Bot[Constant.world_scale[0]][Constant.world_scale[1]];
		oxygen_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		co2_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		org_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		mnr_map = new double[Constant.world_scale[0]][Constant.world_scale[1]];
		//стартовые ресурсы
		for (int x = 0; x < Constant.world_scale[0]; x++) {
			for (int y = 0; y < Constant.world_scale[1]; y++) {
				oxygen_map[x][y] = Constant.starting_ox;
				co2_map[x][y] = Constant.starting_co2;
				org_map[x][y] = Constant.starting_org;
				mnr_map[x][y] = 0;
				Map[x][y] = null;
				height_map[x][y] = (int)(noise.sumOctaves(8, x, y, 0.5F, 0.007F, 0, 1000));
			}
		}
	}

	//
	//ШАГ СИМУЛЯЦИИ (вызывается под lock)
	//
	public void step() {
		steps++;
		b_count = 0;
		obj_count = 0;
		org_count = 0;
		//
		ListIterator<Bot> bot_iterator = objects.listIterator();
		while (bot_iterator.hasNext()) {
			Bot next_bot = bot_iterator.next();
			next_bot.Update(bot_iterator);
			obj_count++;
			if (next_bot.state != 0) {
				org_count++;
			}else {
				b_count++;
			}
		}
		//
		WorldUtils.gas(oxygen_map, org_map);
		WorldUtils.gas(co2_map, org_map);
		WorldUtils.minerals(mnr_map);
		//
		objects.removeIf(b -> b.killed == 1);//один проход вместо удаления по одному из середины списка
		if (selection != null && selection.killed == 1) {
			selection = null;
		}
	}

}
