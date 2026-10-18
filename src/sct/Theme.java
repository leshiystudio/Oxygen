package sct;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

//Цвета интерфейса и фона мира. Цвета ботов в режимах отрисовки не зависят от темы.
public class Theme {
	public static boolean dark = true;
	//
	//фон мира и ресурсы (используются PixelRenderer)
	public static int world_bg;//пустая клетка
	public static int oxygen;//максимум кислорода
	public static int co2;//максимум углекислого газа
	public static int organics;//максимум органики
	public static int minerals;//максимум минералов
	public static int dead_zone;//переизбыток органики
	public static int direction;//черта направления бота
	//
	//панели
	public static Color panel_bg;
	public static Color panel_fg;
	public static Color graph_bg;
	public static Color graph_grid;
	//
	static {
		apply_colors();
	}
	//
	private static void apply_colors() {
		if (dark) {
			world_bg = rgb(18, 18, 22);
			oxygen = rgb(70, 110, 255);
			co2 = rgb(255, 80, 80);
			organics = rgb(190, 150, 90);
			minerals = rgb(210, 210, 210);
			dead_zone = rgb(120, 10, 10);
			direction = rgb(0, 0, 0);
			panel_bg = new Color(43, 43, 48);
			panel_fg = new Color(220, 220, 225);
			graph_bg = new Color(30, 30, 34);
			graph_grid = new Color(60, 60, 66);
		}else {
			world_bg = rgb(255, 255, 255);
			oxygen = rgb(128, 128, 255);
			co2 = rgb(255, 128, 128);
			organics = rgb(0, 0, 0);
			minerals = rgb(0, 0, 0);
			dead_zone = rgb(90, 0, 0);
			direction = rgb(0, 0, 0);
			panel_bg = Color.LIGHT_GRAY;
			panel_fg = Color.BLACK;
			graph_bg = Color.WHITE;
			graph_grid = new Color(220, 220, 220);
		}
	}
	//
	//Nimbus входит в JDK: тёмная тема без внешних библиотек
	public static void install_look_and_feel() {
		try {
			if (dark) {
				UIManager.put("control", new Color(43, 43, 48));
				UIManager.put("info", new Color(60, 60, 66));
				UIManager.put("nimbusBase", new Color(30, 32, 38));
				UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
				UIManager.put("nimbusDisabledText", new Color(128, 128, 128));
				UIManager.put("nimbusFocus", new Color(115, 164, 209));
				UIManager.put("nimbusGreen", new Color(176, 179, 50));
				UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
				UIManager.put("nimbusLightBackground", new Color(30, 30, 34));
				UIManager.put("nimbusOrange", new Color(191, 98, 4));
				UIManager.put("nimbusRed", new Color(169, 46, 34));
				UIManager.put("nimbusSelectedText", new Color(255, 255, 255));
				UIManager.put("nimbusSelectionBackground", new Color(75, 110, 175));
				UIManager.put("text", new Color(220, 220, 225));
			}else {
				for (String key : new String[]{"control", "info", "nimbusBase", "nimbusAlertYellow", "nimbusDisabledText", "nimbusFocus",
						"nimbusGreen", "nimbusInfoBlue", "nimbusLightBackground", "nimbusOrange", "nimbusRed",
						"nimbusSelectedText", "nimbusSelectionBackground", "text"}) {
					UIManager.put(key, null);
				}
			}
			UIManager.setLookAndFeel(new NimbusLookAndFeel());
		}catch (Exception e) {
			System.out.println("Theme: Nimbus недоступен, остается стандартный вид: " + e);
		}
	}
	//
	public static void toggle(java.awt.Component root) {
		dark = !dark;
		apply_colors();
		install_look_and_feel();
		SwingUtilities.updateComponentTreeUI(root);
		root.repaint();
	}
	//
	public static int rgb(int r, int g, int b) {
		return (r << 16) | (g << 8) | b;
	}
	//
	public static int mix(int c1, int c2, double t) {//смешение двух цветов, t в 0..1
		if (t <= 0) {
			return c1;
		}
		if (t >= 1) {
			return c2;
		}
		int r = (int)(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = (int)(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = (int)((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return (r << 16) | (g << 8) | b;
	}
}
