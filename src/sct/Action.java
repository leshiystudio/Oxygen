package sct;

import java.util.ListIterator;

//Действие тела существа: ход, атака, фотосинтез...
//Тело регистрирует свои действия (у бота Oxygen — BotAction). Мозг любого типа — геном,
//нейросеть или свой алгоритм — вызывает их по имени; выключенное действие ничего не делает.
public interface Action<B extends IBot> {
	String id();//короткое имя: "move", "attack", "photo", ... (name() у enum занят)
	String category();//для меню и окна генома
	String[] args();//имена параметров: направление, количество, тип потомка...
	boolean enabled();//включено ли у этого вида (галочка в меню)
	boolean run(B body, ListIterator<IBot> iterator, int... args);//выполнить; false — выключено или не удалось
}
