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
	public static final String NEURO = "neuro";//нейросеть: настройки — у популяции (sct.neuro.NeuroConfig)
	public static final String FLY = "fly";//мозг мухи на видеокарте (sct.neuro.FlyBrain, настройки fly_* в Constant)
	public static final String[] plant_brains = {"think_new", "think_old", NEURO};
	public static String[] bot_brain_names() {
		java.util.List<String> l = new java.util.ArrayList<String>(for_bot.keySet());
		l.add(NEURO);
		l.add(FLY);
		return l.toArray(new String[0]);
	}
	//мозг-прототип популяции; существам — prototype.spawn()
	public static Brain<Bot> make_bot_brain(Population p) {
		if (p.brain.equals(NEURO)) {
			return new sct.neuro.NeuroBrain<Bot>(p.neuro_config(), sct.neuro.BotBody.instance);
		}
		if (p.brain.equals(FLY)) {
			return new sct.neuro.FlyBrain(p.count);
		}
		return bot_brain(p.brain);
	}
	public static Brain<sct.plants.Plant> make_plant_brain(Population p) {
		switch (p.brain) {
			case NEURO: return new sct.neuro.NeuroBrain<sct.plants.Plant>(p.neuro_config(), sct.neuro.PlantBody.instance);
			case "think_old": return sct.plants.ThinkOld.instance;
			default: return sct.plants.ThinkNew.instance;
		}
	}
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
