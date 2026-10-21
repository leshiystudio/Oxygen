package sct;

//Датчики тела: что существо знает о себе и о мире. Читаются нейросетью (входы)
//и своими алгоритмами. Значения — как есть, без нормировки: нормирует тот, кто читает.
public interface Sensors<B extends IBot> {
	String[] names();
	void read(B body, float[] out);//out.length == names().length
}
