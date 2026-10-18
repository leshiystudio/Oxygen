package sct;

import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;

//Реестр мозгов. Новый мозг — класс, реализующий Brain<Bot>, и одна строка в static-блоке.
//Выбор при запуске: --set bot_brain=<имя> (позже — в диалоге «Новый мир»).
public class Brains {
	public static final Map<String, Brain<Bot>> for_bot = new LinkedHashMap<String, Brain<Bot>>();
	static {
		for_bot.put("genome", null);//геном команд (Bot.update_commands)
		for_bot.put("simple", new SimpleBrain());
	}
	//
	public static Brain<Bot> bot_brain(String name) {
		if (!for_bot.containsKey(name)) {
			throw new IllegalArgumentException("нет мозга " + name + ", есть: " + for_bot.keySet());
		}
		return for_bot.get(name);
	}
	//
	//Пример своего алгоритма: копит энергию фотосинтезом (или переработкой, если стал
	//переработчиком), делится в свободную клетку, когда энергии много, а на чужих нападает
	//только когда голоден. Написан на параметрах бота и мира, как обычный код; геном не используется.
	//(«Свой/чужой» по-прежнему считается по геному: если нападать на всех чужих, мир съедает сам себя.)
	public static class SimpleBrain implements Brain<Bot> {
		public String name() {
			return "simple";
		}
		public void think(Bot b, ListIterator<IBot> it) {
			int ahead = b.see(b.rotate);
			if (b.energy > 600) {//энергии много — делиться в свободную клетку, иначе искать ее
				if (ahead == 1) {
					BotAction.DIVIDE.run(b, it, BotAction.FORWARD, 0);
				}else {
					BotAction.RANDOM_DIR.run(b, it);
				}
			}else if (ahead == 4 && b.energy < 150) {//голоден, впереди чужой — атаковать
				BotAction.ATTACK.run(b, it, BotAction.FORWARD);
			}else if (b.pht_org_block == 2) {//переработчик: органика под собой
				BotAction.RECYCLE.run(b, it, -1, 50);
			}else {
				BotAction.PHOTO.run(b, it);
			}
		}
	}
}
