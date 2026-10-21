package sct.neuro;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import sct.Bot;
import sct.BotAction;
import sct.Constant;
import sct.IBot;

//Тело бота Oxygen для нейросети (NeuroBot): датчики BotSensors, действия BotAction.
//Направление: 0..7 — абсолютное, 8 — вперед (куда смотрит). Параметры p0, p1 (0..1) —
//количество, тип потомка, время полета семечка, приказ королевы — по смыслу действия.
public class BotBody implements NeuroBody<Bot> {
	private static final int SEE_CODES = 9;//see(): 0 семечко ... 7 растение, 8 нейро-бот
	private static final String[] see_dirs = {"forward", "left", "right"};
	public static final BotBody instance = new BotBody();//после полей выше: конструктор их использует
	private final String[] inputs;
	private final String[] actions;

	private BotBody() {
		List<String> in = new ArrayList<String>();
		for (String s : new String[]{"energy", "age", "oxygen", "co2", "organics under", "organics forward"}) {
			in.add(s);
		}
		for (String d : see_dirs) {
			for (int k = 0; k < SEE_CODES; k++) {
				in.add("see " + d + "=" + k);
			}
		}
		for (String s : new String[]{"neighbours", "chains", "queen", "subordinate", "has order", "x", "y"}) {
			in.add(s);
		}
		inputs = in.toArray(new String[0]);
		BotAction[] all = BotAction.values();
		actions = new String[all.length];
		for (int i = 0; i < all.length; i++) {
			actions[i] = all[i].id();
		}
	}
	public String[] inputs() {
		return inputs;
	}
	public void read(Bot b, float[] out) {
		int k = 0;
		int[] fwd = b.get_rotate_position(b.rotate);
		out[k++] = (float)(b.energy / 1000.0);
		out[k++] = (float)(b.age / Constant.max_age);
		out[k++] = (float)(b.oxygen() / Constant.ox_render_maximum_coeff);
		out[k++] = (float)(b.co2() / Constant.co2_render_maximum_coeff);
		out[k++] = (float)(b.organics(b.xpos, b.ypos) / Constant.org_die_level);
		out[k++] = (float)(b.organics(fwd[0], fwd[1]) / Constant.org_die_level);
		int[] rots = {b.rotate, (b.rotate + 6) % 8, (b.rotate + 2) % 8};
		for (int r : rots) {
			int code = b.see(r);
			for (int c = 0; c < SEE_CODES; c++) {
				out[k++] = c == code ? 1f : 0f;
			}
		}
		out[k++] = b.bot_count() / 8f;
		out[k++] = b.count_chains() / 8f;
		out[k++] = b.queen == 1 ? 1f : 0f;
		out[k++] = b.queen == 2 ? 1f : 0f;
		out[k++] = b.queen_command >= 0 ? 1f : 0f;
		out[k++] = (float) b.xpos / Constant.world_scale[0];
		out[k++] = (float) b.ypos / Constant.world_scale[1];
	}
	public String[] actions() {
		return actions;
	}
	public boolean enabled(Bot b, int action) {
		return b.on(BotAction.values()[action]);
	}
	public int dirs() {
		return 9;
	}
	public int params() {
		return 2;
	}
	public void act(Bot b, ListIterator<IBot> it, int action, int dir, float[] p) {
		BotAction a = BotAction.values()[action];
		int d = dir == 8 ? BotAction.FORWARD : dir;
		int type = Math.min(7, (int)(p[1] * 8));
		switch (a) {
			case TURN: a.run(b, it, dir % 8); break;
			case SET_DIR: a.run(b, it, dir % 8); break;
			case RECYCLE: a.run(b, it, p[1] < 0.5f ? -1 : d, (int)(p[0] * (Constant.mind_max_value_command - 1) * Constant.org_recycle_coeff)); break;
			case DIVIDE: a.run(b, it, d, type); break;
			case CHAIN_DIVIDE: a.run(b, it, d, type); break;
			case SEED: a.run(b, it, d, 1 + Math.min(15, (int)(p[0] * 16)), type); break;
			case QUEEN_ORDER: a.run(b, it, Math.min(49, (int)(p[0] * 50)), 1 + Math.min(Constant.queen_max_radius - 1, (int)(p[1] * Constant.queen_max_radius)), dir % 3 - 1); break;
			default: a.run(b, it, d);//одно направление или без параметров
		}
	}
}
