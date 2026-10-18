package sct;

import java.util.ListIterator;

//Контракт любого существа мира: бот Oxygen, растение, нейро-бот...
//ВОССТАНОВЛЕНО по индексу Eclipse: первые два метода — исходный интерфейс (для BotCB, сам BotCB утерян).
//Их сигнатура подошла для общего мира как есть: список мира — ArrayList<IBot>, существо
//добавляет потомков через тот же итератор. Остальные методы — общий язык видов
//(docs/multispecies-plan.md, раздел 1).
public interface IBot {
	int Update(ListIterator<IBot> iterator);//ход существа
	void update_commands(ListIterator<IBot> iterator);//мозг
	//
	Species species();
	Population population();//популяция существа (null — вне популяций: тесты, инструменты)
	int cell_x();//клетка мира
	int cell_y();
	boolean alive();
	boolean crowds();//занимает место как сосед при подсчете тесноты (у бота — не семечко)
	double ledger_energy();//сколько энергии существо держит для журнала баланса (у бота — энергия + тело)
	double energy_value();//сколько энергии достанется съевшему
	double eaten_by(IBot eater, double efficiency);//смерть от поедания по правилам своего вида; возвращает энергию съевшему (остаток — в органику/почву)
	void die(int cause);//смерть по причине Ledger.DIE_*
	boolean same_kin(IBot other);//«свой/чужой»; существо другого вида — всегда чужое
	int color(int draw_type);//цвет 0xRRGGBB для отрисовки
	String describe();//для панели и инструмента Info
}
