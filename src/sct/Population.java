package sct;

import java.awt.Color;

//Популяция — строка мира: тело (вид) + мозг + настройки + включенные действия + сколько при старте.
//Две популяции одного тела с разными мозгами или действиями («хищники», «травоядные») — разные строки.
//Взаимодействие популяций задает Interaction (по номерам популяций).
public class Population {
	public int id;//номер в мире (индекс в WorldBio.populations)
	public String name;
	public Species species;//тело
	public String brain;//имя мозга: для ботов — Brains, для растений — think_new / think_old
	public int count;//сколько при создании мира
	public Color color;//цвет популяции (режим отрисовки «популяции», легенда графиков)
	public boolean[] actions_on;//включенные действия тела (BotAction или PlantAction по ordinal)
	public sct.plants.PlantConfig plant_config;//настройки растений (у популяций растений)
	//
	//во время работы мира
	public transient Brain<Bot> bot_brain;
	public transient Brain<sct.plants.Plant> plant_brain;
	public transient sct.plants.PlantWorld plant_world;

	public Population(String name, Species species, String brain, int count, Color color) {
		this.name = name;
		this.species = species;
		this.brain = brain;
		this.count = count;
		this.color = color;
		int n = species == Species.PLANT ? sct.plants.PlantAction.values().length : BotAction.values().length;
		actions_on = new boolean[n];
		java.util.Arrays.fill(actions_on, true);
		if (species == Species.PLANT) {
			plant_config = new sct.plants.PlantConfig();
		}
	}
	//
	public boolean on(int action_ordinal) {
		return actions_on[action_ordinal];
	}
	//выключить действия по именам через запятую (как bot_actions_off / plant_actions_off)
	public void actions_off(String off) {
		java.util.Arrays.fill(actions_on, true);
		for (String name : off.split(",")) {
			name = name.trim();
			if (name.isEmpty()) {
				continue;
			}
			int idx = -1;
			if (species == Species.PLANT) {
				for (sct.plants.PlantAction a : sct.plants.PlantAction.values()) {
					if (a.id().equals(name)) {
						idx = a.ordinal();
					}
				}
			}else {
				BotAction a = BotAction.by_name(name);
				idx = a == null ? -1 : a.ordinal();
			}
			if (idx < 0) {
				throw new IllegalArgumentException("нет действия " + name + " у " + species.title);
			}
			actions_on[idx] = false;
		}
	}
	//
	//популяции по умолчанию — из параметров командной строки (starting_bot_count, starting_plant_count ...)
	public static java.util.List<Population> defaults() {
		java.util.List<Population> list = new java.util.ArrayList<Population>();
		Population bots = new Population("Oxygen bots", Species.OXYGEN_BOT, Constant.bot_brain, Constant.starting_bot_count, new Color(80, 160, 255));
		bots.actions_off(Constant.bot_actions_off);
		list.add(bots);
		if (Constant.starting_plant_count > 0) {
			Population plants = new Population("Plants", Species.PLANT,
				sct.plants.PlantConfig.current.think_mode == sct.plants.PlantConfig.THINK_OLD ? "think_old" : "think_new",
				Constant.starting_plant_count, new Color(40, 200, 90));
			plants.plant_config = sct.plants.PlantConfig.current;
			plants.actions_off(Constant.plant_actions_off);
			list.add(plants);
		}
		for (int i = 0; i < list.size(); i++) {
			list.get(i).id = i;
		}
		return list;
	}
	public String toString() {
		return name;
	}
}
