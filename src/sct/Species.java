package sct;

//Виды существ. Вид определяет тело; мозг и настройки задаются популяцией (этап 4).
//see_code — что видит бот Oxygen командой «посмотреть», когда перед ним существо этого вида
//(для самих ботов Oxygen see() считает код по своим правилам и see_code не использует).
public enum Species {
	OXYGEN_BOT("Oxygen bot", -1),
	PLANT("Plant", 7);
	//следующие этапы: NEURO_BOT("Neuro bot", 8), NEURO_PLANT(...)

	public final String title;
	public final int see_code;

	Species(String title, int see_code) {
		this.title = title;
		this.see_code = see_code;
	}
	public String toString() {//в списках и таблицах; в файлах настроек — name()
		return title;
	}
}
