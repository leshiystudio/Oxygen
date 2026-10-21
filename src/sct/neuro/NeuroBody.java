package sct.neuro;

import java.util.ListIterator;

import sct.IBot;

//Переходник «тело -> нейросеть»: какие у тела датчики (входы) и действия (выходы)
//и как выходы сети превратить в параметры действия. Есть для бота (BotBody) и растения (PlantBody).
//
//Выходы сети: [действия: actions()] [направления: dirs()] [параметры: params()].
public interface NeuroBody<B extends IBot> {
	String[] inputs();//имена входов (после развертки, например "see forward=4")
	void read(B body, float[] out);//входы, нормированные примерно в -1..1
	String[] actions();//имена действий тела (в порядке ordinal)
	boolean enabled(B body, int action);//действие включено у популяции
	int dirs();//сколько вариантов направления
	int params();//сколько числовых параметров
	void act(B body, ListIterator<IBot> it, int action, int dir, float[] params);//выполнить (params — после сигмоиды, 0..1)
}
