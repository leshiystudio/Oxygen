package sct;

import java.awt.Graphics;

//Общая база существ: позиция, смерть, вид. Bot и будущие Plant наследуют ее.
//ВОССТАНОВЛЕНО по индексу Eclipse: исходник утерян, известна только сигнатура Draw.
//В индексе у Draw нет ссылок ни на что, кроме параметров, — тело было пустым; оставлено как есть.
public abstract class BotSuper implements IBot {
	public int xpos;//клетка мира
	public int ypos;
	public int killed = 0;//убито ли существо

	public void Draw(Graphics canvas, int draw_type, int zoom, int[] pos) {
	}
	//
	public int cell_x() {
		return xpos;
	}
	public int cell_y() {
		return ypos;
	}
	public boolean alive() {
		return killed == 0;
	}
}
