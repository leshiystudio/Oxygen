package sct;

import java.util.ListIterator;

//Мозг — алгоритм выбора действий тела. Геном, нейросеть и свой алгоритм — равноправные реализации.
//
//Свой алгоритм — обычный класс: читает параметры тела и мира (поля бота, датчики) и вызывает
//действия, например:
//	if (body.energy < 300) BotAction.PHOTO.run(body, it);
//	else if (body.see(body.rotate) == 4) BotAction.ATTACK.run(body, it, BotAction.FORWARD);
//Чтобы он появился в меню — одна строка в Brains.
public interface Brain<B extends IBot> {
	String name();
	void think(B body, ListIterator<IBot> iterator);//один ход мозга
	default Brain<B> child(B parent) {//мозг потомка; алгоритм без состояния просто передается
		return this;
	}
	default Brain<B> spawn() {//мозг существа при создании мира (нейросеть — своя случайная сеть у каждого)
		return this;
	}
	default boolean can_multiply(B parent) {//может ли существо сейчас дать потомка (муха — пока есть слот)
		return true;
	}
	default void died(B body, int cause) {//существо погибло (cause — Ledger.DIE_*)
	}
	default boolean breathes() {//газообмен животного (муха): шаг — O2 в CO2, еда органикой — CO2 в O2
		return false;
	}
}
