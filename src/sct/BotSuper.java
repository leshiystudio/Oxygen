package sct;

import java.awt.Graphics;

//Общая база существ: позиция, смерть, вид. Bot и будущие Plant наследуют ее.
//ВОССТАНОВЛЕНО по индексу Eclipse: исходник утерян, известна только сигнатура Draw.
//В индексе у Draw нет ссылок ни на что, кроме параметров, — тело было пустым; оставлено как есть.
public abstract class BotSuper implements IBot {
	public int xpos;//клетка мира
	public int ypos;
	public int killed = 0;//убито ли существо
	public Population pop;//популяция (null — вне популяций)

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
	public Population population() {
		return pop;
	}
	//номер популяции для таблицы взаимодействий (-1 — вне популяций)
	public static int pid(IBot o) {
		Population p = o.population();
		return p == null ? -1 : p.id;
	}
	public boolean same_population(IBot o) {
		return o.population() == pop;
	}
	//между разными популяциями: КПД еды, обмен, передача, цепочки, семечко (Interaction)
	public double eat_efficiency(IBot target) {
		int a = pid(this), b = pid(target);
		return a < 0 || b < 0 ? 1.0 : Interaction.current.eat[a][b];
	}
	public double exchange(IBot target) {
		int a = pid(this), b = pid(target);
		return a < 0 || b < 0 ? 1.0 : Interaction.current.exchange[a][b];
	}
	public boolean can_share(IBot target) {
		int a = pid(this), b = pid(target);
		return same_population(target) || (a >= 0 && b >= 0 && Interaction.current.share[a][b]);
	}
	public boolean can_chain(IBot target) {
		int a = pid(this), b = pid(target);
		return same_population(target) || (a >= 0 && b >= 0 && Interaction.current.chain[a][b]);
	}
	public boolean seed_kills(IBot target) {
		int a = pid(this), b = pid(target);
		return same_population(target) || a < 0 || b < 0 || Interaction.current.seed_kills[a][b];
	}
}
