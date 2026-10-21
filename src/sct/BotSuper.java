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
	//счетчики жизни — для отбора лучших при перезапуске мира (Elite); правил мира не меняют
	public int life_steps;//сколько ходов прожил (живым)
	public int life_children;//сколько потомков родил
	public int life_generation;//глубина рода: у потомка — родитель + 1
	public double life_energy;//сколько энергии добыл из мира (солнце, органика, почва, CO2, еда)
	public int life_kills;//сколько существ съел
	public double[] life_values() {//в порядке Elite.FIELDS
		return new double[]{life_steps, life_children, life_energy, life_kills, life_generation};
	}
	public void inherit_life(BotSuper parent) {//потомок: глубина рода, счет у родителя
		life_generation = parent.life_generation + 1;
		parent.life_children++;
	}
	public boolean living() {//живое существо (у растения труп — не живое, хотя еще в мире)
		return alive();
	}
	public Object heritage() {//снимок наследственности для клонов в новом мире (Elite); null — нечего переносить
		return null;
	}

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
