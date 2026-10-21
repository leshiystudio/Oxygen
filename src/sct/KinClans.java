package sct;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

//Кланы по геному: кто свой, кто чужой.
//Порог тот же, что в Bot.see(): геномы, отличающиеся не больше чем на Constant.max_different_genom
//генов, считаются родней.
//
//Как считается (раз в update_every шагов, в потоке симуляции):
//1. Одинаковые геномы собираются в группы.
//2. Группы по убыванию размера раскладываются по «лидерам»: группа попадает к первому лидеру,
//   от которого отличается не больше порога, иначе сама становится новым лидером.
//   Это приближение: точное разбиение на кланы (связность по порогу) стоило бы O(n^2).
//3. Цвет клана сохраняется между пересчетами: новый лидер, похожий на прежнего, берет его цвет.
public class KinClans {
	public static int update_every = 50;
	public static int max_leaders = 3000;//больше — серый «прочие», чтобы пересчет не затягивался
	public static int unique_genomes = 0;
	public static int clans = 0;
	//
	private static final int other_color = 0x606060;
	private static final Random rand = new Random(7);
	private static List<Leader> leaders = new ArrayList<Leader>();
	//
	private static class Leader {
		int[][] genom;
		int color;
		int size;
		Leader(int[][] genom, int color) {
			this.genom = genom;
			this.color = color;
		}
	}
	private static class Group {
		int[][] genom;
		ArrayList<Bot> bots = new ArrayList<Bot>();
		Group(int[][] genom) {
			this.genom = genom;
		}
	}
	//
	public static void update(WorldBio w) {
		//1. одинаковые геномы
		HashMap<Long, ArrayList<Group>> by_hash = new HashMap<Long, ArrayList<Group>>();
		ArrayList<Group> groups = new ArrayList<Group>();
		for (IBot o : w.objects) {
			if (!o.alive() || !(o instanceof Bot b)) {//кланы по геному — пока для ботов Oxygen
				continue;
			}
			long h = hash(b.genom_array);
			ArrayList<Group> list = by_hash.computeIfAbsent(h, k -> new ArrayList<Group>());
			Group found = null;
			for (Group g : list) {
				if (Arrays.deepEquals(g.genom, b.genom_array)) {
					found = g;
					break;
				}
			}
			if (found == null) {
				found = new Group(b.genom_array);
				list.add(found);
				groups.add(found);
			}
			found.bots.add(b);
		}
		unique_genomes = groups.size();
		//2. раскладка по лидерам
		groups.sort((a, b) -> b.bots.size() - a.bots.size());
		int limit = Constant.max_different_genom;
		List<Leader> next = new ArrayList<Leader>();
		for (Group g : groups) {
			Leader leader = null;
			for (Leader l : next) {
				if (diff_limited(g.genom, l.genom, limit) <= limit) {
					leader = l;
					break;
				}
			}
			if (leader == null && next.size() < max_leaders) {
				leader = new Leader(g.genom, inherit_color(g.genom, limit));
				next.add(leader);
			}
			int color = leader == null ? other_color : leader.color;
			int id = leader == null ? -1 : next.indexOf(leader);
			if (leader != null) {
				leader.size += g.bots.size();
			}
			for (Bot b : g.bots) {
				b.kin_color = color;
				b.kin_id = id;
			}
		}
		leaders = next;
		clans = next.size();
	}
	//
	private static int inherit_color(int[][] genom, int limit) {//3. цвет от похожего прежнего лидера
		for (Leader l : leaders) {
			if (diff_limited(genom, l.genom, limit) <= limit) {
				return l.color;
			}
		}
		Color c = Color.getHSBColor(rand.nextFloat(), 0.55f + rand.nextFloat() * 0.45f, 0.65f + rand.nextFloat() * 0.35f);
		return c.getRGB() & 0xFFFFFF;
	}
	//
	public static int diff_limited(int[][] a, int[][] b, int limit) {//число различий, счет останавливается после limit
		int diff = 0;
		for (int i = 0; i < a.length; i++) {
			int[] ai = a[i], bi = b[i];
			int n = ai.length;
			for (int j = 0; j < n; ) {//от различия к различию, совпадающие участки — векторно
				int m = Arrays.mismatch(ai, j, n, bi, j, n);
				if (m < 0) {
					break;
				}
				if (++diff > limit) {
					return diff;
				}
				j += m + 1;
			}
		}
		return diff;
	}
	//
	private static long hash(int[][] g) {//FNV-1a по всем генам
		long h = 0xcbf29ce484222325L;
		for (int[] row : g) {
			for (int v : row) {
				h ^= v;
				h *= 0x100000001b3L;
			}
		}
		return h;
	}
}
