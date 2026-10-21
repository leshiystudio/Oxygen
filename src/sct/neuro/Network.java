package sct.neuro;

import java.util.Random;

//Перцептрон: слои sizes[0] (входы) -> ... -> sizes[last] (выходы). Веса и смещения — в одном массиве.
//Скрытые слои — с функцией активации, выходной — линейный (дальше решает выбор действия).
public class Network {
	public final int[] sizes;
	public final float[] w;
	private final int act;//0 tanh, 1 relu, 2 sigmoid
	private final float[][] buf;//выходы слоев — без выделения памяти на каждый ход

	public Network(int[] sizes, String activation) {
		this.sizes = sizes;
		int n = 0;
		for (int l = 1; l < sizes.length; l++) {
			n += (sizes[l - 1] + 1) * sizes[l];//веса + смещение
		}
		this.w = new float[n];
		this.act = activation.equals("relu") ? 1 : activation.equals("sigmoid") ? 2 : 0;
		this.buf = new float[sizes.length][];
		for (int l = 0; l < sizes.length; l++) {
			buf[l] = new float[sizes[l]];
		}
	}
	public Network copy() {
		Network c = new Network(sizes, act == 1 ? "relu" : act == 2 ? "sigmoid" : "tanh");
		System.arraycopy(w, 0, c.w, 0, w.length);
		return c;
	}
	public void randomize(Random r, double sigma) {
		for (int i = 0; i < w.length; i++) {
			w[i] = (float)(r.nextGaussian() * sigma);
		}
	}
	public int mutate(Random r, double rate, double sigma) {//возвращает число измененных весов
		int changed = 0;
		for (int i = 0; i < w.length; i++) {
			if (r.nextDouble() < rate) {
				w[i] += (float)(r.nextGaussian() * sigma);
				changed++;
			}
		}
		return changed;
	}
	public double distance(Network o) {//средняя абсолютная разница весов
		if (o.w.length != w.length) {
			return Double.MAX_VALUE;
		}
		double s = 0;
		for (int i = 0; i < w.length; i++) {
			s += Math.abs(w[i] - o.w[i]);
		}
		return w.length == 0 ? 0 : s / w.length;
	}
	public float[] forward(float[] input) {
		System.arraycopy(input, 0, buf[0], 0, sizes[0]);
		int k = 0;
		for (int l = 1; l < sizes.length; l++) {
			float[] in = buf[l - 1];
			float[] out = buf[l];
			int n_in = sizes[l - 1];
			boolean last = l == sizes.length - 1;
			for (int j = 0; j < sizes[l]; j++) {
				float s = w[k + n_in];//смещение
				for (int i = 0; i < n_in; i++) {
					s += w[k + i] * in[i];
				}
				k += n_in + 1;
				out[j] = last ? s : activate(s);
			}
		}
		return buf[sizes.length - 1];
	}
	private float activate(float s) {
		switch (act) {
			case 1: return s > 0 ? s : 0;
			case 2: return (float)(1 / (1 + Math.exp(-s)));
			default: return (float) Math.tanh(s);
		}
	}
}
