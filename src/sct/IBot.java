package sct;

import java.util.ListIterator;

//ВОССТАНОВЛЕНО по индексу Eclipse: интерфейс для BotCB (сам BotCB утерян).
public interface IBot {
	int Update(ListIterator<IBot> iterator);
	void update_commands(ListIterator<IBot> iterator);
}
