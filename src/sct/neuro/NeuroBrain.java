package sct.neuro;

import java.util.ListIterator;
import java.util.Random;

import sct.Brain;
import sct.IBot;
import sct.Ledger;

//Нейросеть как мозг любого тела. У каждого существа своя сеть (свои веса);
//потомок получает копию с мутацией. За ход — одно действие.
public class NeuroBrain<B extends IBot> implements Brain<B> {
	public static Random rand = new Random();//генератор нейросетей; зерно — Presets.apply_seed
	//
	public final NeuroConfig c;
	public final NeuroBody<B> body;
	public final Network net;
	private final float[] in;
	private final float[] memory;
	private final boolean[] off;//выключенные датчики
	private final int n_in, n_act, n_dir, n_par;

	public NeuroBrain(NeuroConfig c, NeuroBody<B> body) {//новая сеть со случайными (или нулевыми) весами
		this(c, body, null);
		if (c.init.equals("zero")) {
			java.util.Arrays.fill(net.w, 0f);
		}else {
			net.randomize(rand, c.init_sigma);
		}
	}
	private NeuroBrain(NeuroConfig c, NeuroBody<B> body, Network net) {
		this.c = c;
		this.body = body;
		String[] names = body.inputs();
		n_in = names.length;
		n_act = body.actions().length;
		n_dir = body.dirs();
		n_par = body.params();
		int[] hidden = c.hidden_sizes();
		int[] sizes = new int[hidden.length + 2];
		sizes[0] = n_in + c.memory;
		System.arraycopy(hidden, 0, sizes, 1, hidden.length);
		sizes[sizes.length - 1] = n_act + n_dir + n_par + c.memory;
		this.net = net != null ? net : new Network(sizes, c.activation);
		in = new float[sizes[0]];
		memory = new float[c.memory];
		off = new boolean[n_in];
		for (int i = 0; i < n_in; i++) {
			off[i] = c.sensor_off(names[i]);
		}
	}
	public String name() {
		return "neuro";
	}
	public Brain<B> spawn() {//отдельная сеть для каждого существа при создании мира
		return new NeuroBrain<B>(c, body);
	}
	public NeuroBrain<B> exact_copy() {//те же веса без мутации, своя память — клон в новом мире (Elite)
		return new NeuroBrain<B>(c, body, net.copy());
	}
	public Brain<B> child(B parent) {
		NeuroBrain<B> ch = new NeuroBrain<B>(c, body, net.copy());
		if (rand.nextDouble() < c.mutation_chance && ch.net.mutate(rand, c.mutation_rate, c.mutation_sigma) > 0) {
			Ledger.mutations++;
		}
		return ch;
	}
	public boolean same_kin(NeuroBrain<?> o) {
		return net.distance(o.net) <= c.kin_threshold;
	}
	//
	public void think(B b, ListIterator<IBot> it) {
		body.read(b, in);
		for (int i = 0; i < n_in; i++) {
			if (off[i]) {
				in[i] = 0;
			}
		}
		System.arraycopy(memory, 0, in, n_in, memory.length);
		float[] out = net.forward(in);
		int action = choose(b, out);
		int dir = 0;
		for (int d = 1; d < n_dir; d++) {
			if (out[n_act + d] > out[n_act + dir]) {
				dir = d;
			}
		}
		float[] params = new float[n_par];
		for (int p = 0; p < n_par; p++) {
			params[p] = (float)(1 / (1 + Math.exp(-out[n_act + n_dir + p])));
		}
		for (int m = 0; m < memory.length; m++) {
			memory[m] = (float) Math.tanh(out[n_act + n_dir + n_par + m]);
		}
		if (action >= 0) {
			body.act(b, it, action, dir, params);
		}
	}
	private int choose(B b, float[] out) {//среди включенных у популяции действий
		if (c.choice.equals("softmax")) {
			double max = Double.NEGATIVE_INFINITY;
			for (int a = 0; a < n_act; a++) {
				if (body.enabled(b, a)) {
					max = Math.max(max, out[a]);
				}
			}
			if (max == Double.NEGATIVE_INFINITY) {
				return -1;
			}
			double t = Math.max(1e-6, c.temperature);
			double sum = 0;
			double[] p = new double[n_act];
			for (int a = 0; a < n_act; a++) {
				p[a] = body.enabled(b, a) ? Math.exp((out[a] - max) / t) : 0;
				sum += p[a];
			}
			double r = rand.nextDouble() * sum;
			for (int a = 0; a < n_act; a++) {
				r -= p[a];
				if (r <= 0 && p[a] > 0) {
					return a;
				}
			}
			return -1;
		}
		int best = -1;
		for (int a = 0; a < n_act; a++) {
			if (body.enabled(b, a) && (best < 0 || out[a] > out[best])) {
				best = a;
			}
		}
		return best;
	}
	//
	//сохранение (WorldIO): веса и память
	public void write(java.io.DataOutputStream out) throws java.io.IOException {
		out.writeInt(net.w.length);
		for (float v : net.w) {
			out.writeFloat(v);
		}
		out.writeInt(memory.length);
		for (float v : memory) {
			out.writeFloat(v);
		}
	}
	public static <B extends IBot> NeuroBrain<B> read(java.io.DataInputStream in, NeuroConfig c, NeuroBody<B> body) throws java.io.IOException {
		NeuroBrain<B> nb = new NeuroBrain<B>(c, body, null);
		int n = in.readInt();
		if (n != nb.net.w.length) {
			throw new java.io.IOException("размер сети в файле " + n + ", по настройкам " + nb.net.w.length);
		}
		for (int i = 0; i < n; i++) {
			nb.net.w[i] = in.readFloat();
		}
		int m = in.readInt();
		for (int i = 0; i < m; i++) {
			float v = in.readFloat();
			if (i < nb.memory.length) {
				nb.memory[i] = v;
			}
		}
		return nb;
	}
}
