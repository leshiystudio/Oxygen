package sct;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

//Зал славы популяции: K лучших существ за серию перезапусков — их наследственность клонируется в новый мир.
//Оценка — в момент смерти (offer из WorldBio.step), когда жизнь закончена и счетчики окончательные:
//ничего не делается на каждом шаге и не перебираются все существа. На смерть — сумма и одно сравнение
//с худшим в архиве; наследственность копируется только у того, кто в архив попал.
//
//Оценка = сумма выбранных полей, каждое поделено на свое среднее по всем смертям популяции: поля разного
//масштаба (ходы — тысячи, потомки — единицы, энергия — сотни тысяч) весят одинаково, «средний» — 1 за поле.
//Куча упорядочена по оценке на момент смерти; best() пересчитывает по итоговым средним.
public class Elite {
	public static final String[] FIELDS = {"lifetime", "children", "energy", "kills", "generation"};
	//
	private record Entry(double score, double[] values, Object heritage) {}
	private final int size;
	private final boolean[] use = new boolean[FIELDS.length];
	private final double[] sum = new double[FIELDS.length];
	private long n = 0;//смертей учтено
	private final PriorityQueue<Entry> heap = new PriorityQueue<Entry>((a, b) -> Double.compare(a.score, b.score));//наверху — худший

	public Elite(int size, String fields) {
		this.size = Math.max(1, size);
		for (String f : fields.split(",")) {
			int i = index(f.trim());
			if (i >= 0) {
				use[i] = true;
			}
		}
	}
	public static int index(String field) {
		for (int i = 0; i < FIELDS.length; i++) {
			if (FIELDS[i].equals(field)) {
				return i;
			}
		}
		return -1;
	}
	public static String check_fields(String fields) {//ошибка в списке полей — исключение с подсказкой
		for (String f : fields.split(",")) {
			if (!f.isBlank() && index(f.trim()) < 0) {
				throw new IllegalArgumentException("нет поля оценки " + f.trim() + ", есть: " + String.join(", ", FIELDS));
			}
		}
		return fields;
	}
	//
	public void offer(BotSuper o) {//существо закончило жизнь
		double[] v = o.life_values();
		n++;
		for (int i = 0; i < v.length; i++) {
			sum[i] += v[i];
		}
		double s = score(v);
		if (heap.size() >= size && s <= heap.peek().score) {//не лучше худшего — ничего не копируем
			return;
		}
		Object h = o.heritage();
		if (h == null) {
			return;
		}
		if (heap.size() >= size) {
			heap.poll();
		}
		heap.add(new Entry(s, v, h));
	}
	private double score(double[] v) {
		double s = 0;
		for (int i = 0; i < v.length; i++) {
			if (use[i] && sum[i] > 0) {
				s += v[i] / (sum[i] / n);
			}
		}
		return s;
	}
	public int count() {
		return heap.size();
	}
	public List<Object> best() {//наследственность лучших, от лучшего к худшему (по итоговым средним)
		List<Entry> all = new ArrayList<Entry>(heap);
		all.sort((a, b) -> Double.compare(score(b.values), score(a.values)));
		List<Object> r = new ArrayList<Object>(all.size());
		for (Entry e : all) {
			r.add(e.heritage);
		}
		return r;
	}
	public double best_score() {
		double best = 0;
		for (Entry e : heap) {
			best = Math.max(best, score(e.values));
		}
		return best;
	}
}
