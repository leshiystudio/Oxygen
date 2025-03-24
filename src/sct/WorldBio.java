package sct;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;

//import sct.World.BotListener;

import java.awt.BasicStroke;

//import sct.World.BotListener;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
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

public class WorldBio extends JPanel implements MouseWheelListener, MouseListener, MouseMotionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ArrayList<Bot> objects;
	Bot[][] Map = new Bot[Constant.world_scale[0]][Constant.world_scale[1]];
	Random rand = new Random();
	Timer timer;
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
	private boolean render = true;
	protected boolean render_border = false;
	protected boolean pause = false;
	// старый код --
	int zoom = 0;
	int[] zoom_disp_pos = {100, 100};
	//--
	// параметры
	int delay = 10;//скорость
	int steps = 0;//количество шагов симуляции
	int b_count = 0;//количество ботов
	int obj_count = 0;//количество объектов
	int org_count = 0;//количество семян
	int draw_type = 0;//режим отрисовки
	int res_draw_type = 0;//режим отрисовки фона/ресурсов/окружения
	

	public WorldBio() {
		setLayout(null);
		timer = new Timer(delay, new BotListener());
		objects = new ArrayList<Bot>();
		setBackground(new Color(255, 255, 255));
		setBackground(new Color(255, 255, 255));
		initComponent();
		
		newPopulation();
		
		timer.start();
		JButton button = new JButton("Кнопка Test");
		add(button);
		
	}

	private void initComponent() {
		addMouseWheelListener(this);
		addMouseMotionListener(this);
		addMouseListener(this);
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		boolean draggerOld = dragger;

		Graphics2D g2 = (Graphics2D) g;
		int width = Constant.world_scale[0] * Constant.size;
		int height = Constant.world_scale[1] * Constant.size;

		BufferedImage buff4 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2dTail = buff4.createGraphics();
		g2dTail.setColor(Color.WHITE);
		g2dTail.fillRect(0, 0, width, height);
		
		AffineTransform at = new AffineTransform();
		
		if (zoomer) {
			double xRel = MouseInfo.getPointerInfo().getLocation().getX() - getLocationOnScreen().getX();
			double yRel = MouseInfo.getPointerInfo().getLocation().getY() - getLocationOnScreen().getY();
			//System.out.println("xRel: "+xRel+", yRel: "+yRel);

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
			g2dTail.setColor(new Color(50, 50, 200));
			g2dTail.fillRect(-100, -100, 0, 0);
			g2dTail.setColor(new Color(50, 250, 50));
			g2dTail.fillRect(0, 0, 100, 100);
			g2dTail.drawRect(0, 0, width, height);			
	    }

		//------------------------------------------
		//Отрисовываем мир на тайле
		if (render) {
			if (res_draw_type != 0) {
				if (res_draw_type == 1) {
					DrawBio.draw_ox(g2dTail, oxygen_map, org_map, zoom, zoom_disp_pos);
				}else if (res_draw_type == 2){
					DrawBio.draw_org(g2dTail, org_map, zoom, zoom_disp_pos);
				}else if (res_draw_type == 3) {
					DrawBio.draw_mnr(g2dTail, mnr_map);
				}else if (res_draw_type == 4) {
					DrawBio.draw_co2(g2dTail, co2_map, org_map, zoom, zoom_disp_pos);
				}else if (res_draw_type == 5) {
					DrawBio.draw_height(g2dTail, height_map, zoom, zoom_disp_pos);
				}
			}
			for(Bot b: objects) {
				b.Draw(g2dTail, draw_type, zoom, zoom_disp_pos);
			}
		}		
		//------------------------------------------

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

	    int xTailDrawCount = 0;
	    int yTailDrawCount = 0;
	    
	    // Отрисовка тайлов в видимой области
	    for (int x = (visibleXStart / tileWidth) * tileWidth; x < visibleXEnd; x += tileWidth) {
	    	xTailDrawCount++;
	    	yTailDrawCount=0;
	        for (int y = (visibleYStart / tileHeight) * tileHeight; y < visibleYEnd; y += tileHeight) {
	        	yTailDrawCount++;
	            // Отрисовка тайла
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

			// попытка отрисовать статичные объекты
//			g2.setStroke(new BasicStroke( (int)Math.ceil(4 / zoomFactor) ));
//			g2.setColor(Color.RED);
//			g2.drawRect( (int) Math.ceil( (getWidth()-xOffsetTile-200) * 1/zoomFactor ), (int) Math.ceil(-yOffsetTile * 1/zoomFactor), (int) Math.ceil(200*1/zoomFactor), (int) Math.ceil(getHeight()*1/zoomFactor) );
	    	
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
		// TODO Auto-generated method stub

	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		Point curPoint = e.getLocationOnScreen();
		xDiff = curPoint.x - startPoint.x;
		yDiff = curPoint.y - startPoint.y;

		int x = e.getX();
		int y = e.getY();

		int xLocal = Constant.mod((int)((x-xOffset) / zoomFactor / Constant.size), Constant.world_scale[0] );
		int yLocal = Constant.mod((int)((y-yOffset) / zoomFactor / Constant.size), Constant.world_scale[1] );
		
		Point p = e.getPoint();
		System.out.println("MouseEvent. x: "+xLocal+", y: "+yLocal+", Point: "+p);
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
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	public void newPopulation() {//создать случайную популяцию
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
		repaint();
	}
	public void kill_all() {//очистить мир
		steps = 0;
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
	//МЫШЬ И ОБНОВЛЕНИЕ МИРА
	//
	private class BotListener extends MouseAdapter implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			if (!pause) {
				update();
			}
			//
			ListIterator<Bot> iterator = objects.listIterator();
			while (iterator.hasNext()) {
				Bot next_bot = iterator.next();
				if (next_bot.killed == 1) {
					iterator.remove();
				}
			}
			//
			repaint();
		}
	}
	//шаг симуляции
	public void update() {
		for (int i = 0; i < 5; i++) {
			steps++;
			b_count = 0;
			obj_count = 0;
			org_count = 0;
			//
			ListIterator<Bot> bot_iterator = objects.listIterator();
			while (bot_iterator.hasNext()) {
				Bot next_bot = bot_iterator.next();
				next_bot.Update(bot_iterator);
//				if (selection != null) {
//					if (next_bot.xpos == selection.xpos && next_bot.ypos == selection.ypos) {
//						if (next_bot != selection) {
//							selection = null;
//							save_button.setEnabled(false);
//							show_brain_button.setEnabled(false);
//							sh_brain = false;
//						}
//					}
//				}
				obj_count++;
				if (next_bot.state != 0) {
					org_count++;
				}else {
					b_count++;
				}
			}
			//
//			if (selection != null) {
//				if (selection.killed == 1 || Map[selection.xpos][selection.ypos] == null || selection.state != 0){
//					selection = null;
//					save_button.setEnabled(false);
//					show_brain_button.setEnabled(false);
//					sh_brain = false;
//				}
//			}
			//
			WorldUtils.gas(oxygen_map, org_map);
			WorldUtils.gas(co2_map, org_map);
			WorldUtils.minerals(mnr_map);
//			//
//			if (rec && steps % 25 == 0) {
//				record();
//			}
		}
	}	
	
}
