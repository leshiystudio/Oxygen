package sct;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sct.neuro.NeuroBrain;
import sct.plants.Plant;
import sct.plants.PlantConfig;

//Снимок мозга существа в клетке — JSON для веб-просмотра (GET /api/brain?x=&y=). Те же данные и та же
//расшифровка, что в окне генома (GenomeView), только один раз, а не живьем.
//  mode = bot_genome  — геномы команд бота: genomes[g][i] = [значение, цвет, номер расшифровки в texts]
//         plant_new / plant_old — геном растения rows x cols, те же тройки
//         neuro       — сеть: sizes, weights (слой за слоем, у нейрона — веса входов и bias), имена входов и выходов
//         none        — в клетке никого
//Читать под w.lock.
public class BrainInfo {
	public static String json(WorldBio w, int x, int y) {
		x = Constant.mod(x, Constant.world_scale[0]);
		y = Constant.mod(y, Constant.world_scale[1]);
		StringBuilder sb = new StringBuilder("{");
		sb.append("\"x\":").append(x).append(",\"y\":").append(y)
			.append(",\"step\":").append(w.steps).append(",\"world\":").append(w.population)
			.append(",\"time\":").append(System.currentTimeMillis());
		IBot o = w.Map[x][y];
		if (o == null || !o.alive()) {
			return sb.append(",\"mode\":\"none\"}").toString();
		}
		Population p = o.population();
		sb.append(",\"species\":").append(Server.str(o.species().name()))
			.append(",\"population\":").append(p == null ? "null" : Server.str(p.name))
			.append(",\"color\":").append(p == null ? "null" : Server.str(hex(p.color)))
			.append(",\"brain\":").append(Server.str(WorldBio.brain_name(o)))
			.append(",\"title\":").append(Server.str(GenomeView.header(o)))
			.append(",\"describe\":").append(Server.str(o.describe()));
		Texts texts = new Texts();
		if (o instanceof Bot b && b.brain instanceof NeuroBrain<?> nb) {
			neuro(sb, nb);
		}else if (o instanceof Plant pl && pl.brain instanceof NeuroBrain<?> nb) {
			neuro(sb, nb);
		}else if (o instanceof Bot b) {
			sb.append(",\"mode\":\"bot_genome\",\"active\":").append(b.genom_current_idx).append(",\"index\":").append(b.index);
			if (b.brain != null) {
				sb.append(",\"note\":").append(Server.str("brain '" + b.brain.name() + "' does not use the genome — shown for lineage"));
			}
			sb.append(",\"cols\":8,\"genomes\":[");
			for (int g = 0; g < b.genom_array.length; g++) {
				sb.append(g == 0 ? "[" : ",[");
				for (int i = 0; i < b.genom_array[g].length; i++) {
					int v = b.genom_array[g][i];
					int cat = Commands.category(v);
					cell(sb, i, v, Commands.category_colors[cat], texts.id(Commands.name(v) + "   (" + Commands.category_names[cat] + ")"));
				}
				sb.append("]");
			}
			sb.append("],\"legend\":[");
			for (int c = 0; c < Commands.category_names.length; c++) {
				sb.append(c == 0 ? "" : ",").append("[").append(Server.str(Commands.category_names[c])).append(",")
					.append(Server.str(hex(Commands.category_colors[c]))).append("]");
			}
			sb.append("]");
		}else if (o instanceof Plant pl) {
			PlantConfig pc = pl.w.c;
			boolean old = WorldBio.brain_name(pl).equals("think_old");
			sb.append(",\"mode\":").append(old ? "\"plant_old\"" : "\"plant_new\"")
				.append(",\"rows\":").append(pc.genome_rows).append(",\"cols\":").append(pc.genome_cols)
				.append(",\"active\":").append(Math.floorMod(old ? pl.cmd : pl.gen, old ? Math.max(1, pl.genome.length) : pc.genome_rows))
				.append(",\"cells\":[");
			for (int idx = 0; idx < pl.genome.length; idx++) {
				int v = pl.genome[idx] & 0xFF;
				int c = idx % pc.genome_cols;
				Color color = old ? GenomeView.old_color(v) : GenomeView.new_color(pc, c, v);
				cell(sb, idx, v, color, texts.id(old ? GenomeView.old_decode(v) : GenomeView.new_decode(pc, c, v)));//позицию подписывает страница
			}
			sb.append("]");
		}else {
			sb.append(",\"mode\":\"other\"");
		}
		sb.append(",\"texts\":[");
		for (int i = 0; i < texts.list.size(); i++) {
			sb.append(i == 0 ? "" : ",").append(Server.str(texts.list.get(i)));
		}
		return sb.append("]}").toString();
	}
	private static void neuro(StringBuilder sb, NeuroBrain<?> nb) {
		int[] sizes = nb.net.sizes;
		sb.append(",\"mode\":\"neuro\",\"sizes\":[");
		for (int i = 0; i < sizes.length; i++) {
			sb.append(i == 0 ? "" : ",").append(sizes[i]);
		}
		sb.append("],\"note\":").append(Server.str(String.format("activation %s, memory %d, choice %s",
			nb.c.activation, nb.c.memory, nb.c.choice)));
		sb.append(",\"in_names\":").append(names(GenomeView.in_names(nb)))
			.append(",\"out_names\":").append(names(GenomeView.out_names(nb)))
			.append(",\"weights\":[");
		float[] wts = nb.net.w;
		for (int i = 0; i < wts.length; i++) {
			sb.append(i == 0 ? "" : ",").append(Float.isFinite(wts[i]) ? String.valueOf(wts[i]) : "0");
		}
		sb.append("]");
	}
	private static void cell(StringBuilder sb, int i, int v, Color c, int text) {
		sb.append(i == 0 ? "" : ",").append("[").append(v).append(",\"").append(hex(c)).append("\",").append(text).append("]");
	}
	private static String names(String[] a) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < a.length; i++) {
			sb.append(i == 0 ? "" : ",").append(Server.str(a[i]));
		}
		return sb.append("]").toString();
	}
	private static String hex(Color c) {
		return String.format("#%06X", c.getRGB() & 0xFFFFFF);
	}
	private static class Texts {//одинаковые расшифровки — один раз
		final List<String> list = new ArrayList<String>();
		final Map<String, Integer> ids = new HashMap<String, Integer>();
		int id(String t) {
			return ids.computeIfAbsent(t, k -> {
				list.add(k);
				return list.size() - 1;
			});
		}
	}
}
