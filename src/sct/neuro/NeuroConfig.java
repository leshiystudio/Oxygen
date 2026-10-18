package sct.neuro;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

//Параметры нейросети популяции (вкладка Neuro, --set neuro.<имя> для популяций по умолчанию).
//Сеть — перцептрон: входы (датчики тела + память) -> скрытые слои -> выходы (действия, направление, параметры).
//Веса наследуются с мутацией (нейроэволюция), при жизни сеть не учится.
public class NeuroConfig {
	public String hidden = "16";//нейронов в скрытых слоях через запятую: "16", "24,12"; пусто — без скрытых слоев
	public String activation = "tanh";//функция активации скрытых слоев: tanh, relu, sigmoid
	public int memory = 0;//ячеек памяти: столько выходов подается на вход в следующем ходу (0 — без памяти)
	public String choice = "argmax";//выбор действия: argmax (самый сильный выход) или softmax (случайно по весам)
	public double temperature = 1.0;//для softmax: меньше — решительнее, больше — случайнее
	public String init = "random";//начальные веса: random (нормальные с init_sigma) или zero
	public double init_sigma = 1.0;
	public double mutation_chance = 0.5;//вероятность, что у потомка сеть мутирует
	public double mutation_rate = 0.05;//доля весов, которые мутируют
	public double mutation_sigma = 0.2;//размер мутации веса
	public boolean kin_by_weights = true;//«свой/чужой» по расстоянию между весами (иначе — по геному тела)
	public double kin_threshold = 0.1;//средняя разница весов, ниже которой — «свой»
	public String sensors_off = "";//выключенные датчики через запятую (вход всегда 0); имена — на вкладке Neuro
	//
	public int[] hidden_sizes() {
		String t = hidden.trim();
		if (t.isEmpty()) {
			return new int[0];
		}
		String[] parts = t.split(",");
		int[] r = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			r[i] = Integer.parseInt(parts[i].trim());
			if (r[i] < 1) {
				throw new IllegalArgumentException("размер слоя должен быть >= 1: " + hidden);
			}
		}
		return r;
	}
	public boolean sensor_off(String name) {
		for (String s : sensors_off.split(",")) {
			if (s.trim().equals(name)) {
				return true;
			}
		}
		return false;
	}
	//
	public void set(String name, String value) {//поле по имени (диалог, файлы настроек)
		try {
			Field f = NeuroConfig.class.getField(name);
			Class<?> t = f.getType();
			if (t == int.class) {
				f.setInt(this, Integer.parseInt(value));
			}else if (t == double.class) {
				f.setDouble(this, Double.parseDouble(value));
			}else if (t == boolean.class) {
				f.setBoolean(this, Boolean.parseBoolean(value));
			}else {
				f.set(this, value);
			}
		}catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("нет параметра нейросети " + name);
		}catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e.toString());
		}
		if (name.equals("hidden")) {
			hidden_sizes();//проверка сразу
		}
	}
	public NeuroConfig copy() {
		NeuroConfig c = new NeuroConfig();
		for (Field f : NeuroConfig.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers())) {
				try {
					f.set(c, f.get(this));
				}catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		return c;
	}
	public static NeuroConfig current = new NeuroConfig();//для популяций по умолчанию (--set neuro.<имя>)
}
