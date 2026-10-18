package sct;

//Эталонный тест: правила бота Oxygen не изменились.
//
//Для нескольких зерен мир из одних ботов Oxygen проходит steps шагов, и отпечаток его полного
//состояния (все поля ботов, геномы, карты среды, порядок ботов в списке) сравнивается
//с записанным эталоном. Любая правка, меняющая поведение хоть в одном бите, его ломает.
//
//  java -Djava.awt.headless=true -cp out sct.GoldenTest            проверка
//  java -Djava.awt.headless=true -cp out sct.GoldenTest --record   напечатать новые эталоны
//
//Эталоны записаны на main (коммит a7f7f82) — до переделок feature/multispecies.
//Перезаписывать их можно только при осознанном изменении правил Oxygen.
//
//Покрытие: за эти шаги каждая из 64 команд генома выполняется сотни тысяч раз, королевы появляются.
//Не ловятся только пограничные значения порогов, которые за 300 шагов не встречаются
//(например, max_different_genom 10 -> 11: пары геномов с разницей ровно 11 генов редки).
public class GoldenTest {
	static final long[] seeds = {1, 2, 3};
	static final int steps = 300;
	static final long[] expected = {
		5126434575677867723L,//seed 1
		-2419687753716356376L,//seed 2
		1461997061335205558L,//seed 3
	};

	public static void main(String[] args) {
		boolean record = args.length > 0 && args[0].equals("--record");
		int failed = 0;
		for (int i = 0; i < seeds.length; i++) {
			long h = run(seeds[i]);
			if (record) {
				System.out.println("\t\t" + h + "L,//seed " + seeds[i]);
			}else if (h == expected[i]) {
				System.out.println("  ok    seed " + seeds[i]);
			}else {
				System.out.println("  FAIL  seed " + seeds[i] + ": " + h + " instead of " + expected[i]);
				failed++;
			}
		}
		if (!record) {
			System.out.println(failed == 0 ? "golden: the rules of Oxygen bots are unchanged" : "golden: " + failed + " of " + seeds.length + " differ");
		}
		System.exit(failed == 0 ? 0 : 1);
	}
	//
	static long run(long seed) {
		Presets.seed = seed;
		WorldBio w = new WorldBio(false);
		for (int i = 0; i < steps; i++) {
			w.step();
		}
		return fingerprint(w);
	}
	//
	static long fingerprint(WorldBio w) {
		Hash h = new Hash();
		h.add(w.steps);
		h.add(w.objects.size());
		for (IBot o : w.objects) {
			Bot b = (Bot) o;//эталон — мир из одних ботов Oxygen
			h.add(b.xpos);
			h.add(b.ypos);
			h.add(b.killed);
			h.add(b.state);
			h.add(b.energy);
			h.add(b.age);
			h.add(b.type);
			h.add(b.rotate);
			h.add(b.index);
			h.add(b.genom_current_idx);
			h.add(b.queen);
			h.add(b.queen_command);
			h.add(b.seed_time);
			h.add(b.pht_org_block);
			h.add(b.c_red);
			h.add(b.c_green);
			h.add(b.c_blue);
			h.add(b.color.getRGB());
			h.add(b.clan_color.getRGB());
			h.add(b.queen_bot == null ? -1 : b.queen_bot.xpos * 100000 + b.queen_bot.ypos);
			for (int i = 0; i < 8; i++) {
				h.add(b.has_chain(i) ? 1 : 0);
			}
			for (int[] g : b.genom_array) {
				for (int v : g) {
					h.add(v);
				}
			}
		}
		for (double[][] m : new double[][][]{w.oxygen_map, w.co2_map, w.org_map, w.mnr_map}) {
			for (double[] row : m) {
				for (double v : row) {
					h.add(v);
				}
			}
		}
		return h.value;
	}
	//
	static class Hash {//FNV-1a по 64-битным словам
		long value = 0xcbf29ce484222325L;
		void add(long v) {
			value ^= v;
			value *= 0x100000001b3L;
		}
		void add(double v) {
			add(Double.doubleToLongBits(v));
		}
	}
}
