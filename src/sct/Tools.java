package sct;

import javax.swing.SwingUtilities;

//Инструменты правой кнопки мыши. Текущий выбирается на панели (или клавишами [ и ]),
//Esc сбрасывает инструмент на «Select» и снимает выделение.
//Новый инструмент — класс, реализующий Tool, добавленный в список all.
//apply вызывается под WorldBio.lock; o — живое существо в клетке (бот, растение, нейро...) или null.
public class Tools {
	public interface Tool {
		String name();
		void apply(WorldBio w, IBot o, int x, int y);
	}
	//
	public static final Tool[] all = {
		new Tool() {//выбрать существо; повторный щелчок по выбранному снимает выделение
			public String name() { return "Select"; }
			public void apply(WorldBio w, IBot o, int x, int y) {
				w.select(o == w.selection ? null : o);
			}
		},
		new Tool() {//выбрать и открыть окно генома
			public String name() { return "Genome"; }
			public void apply(WorldBio w, IBot o, int x, int y) {
				if (o != null) {
					w.select(o);
					SwingUtilities.invokeLater(() -> GenomeView.show(SwingUtilities.getWindowAncestor(w), w));
				}
			}
		},
		new Tool() {//убить: энергия уходит в органику (бот) или почву и труп (растение) — как при обычной смерти
			public String name() { return "Remove"; }
			public void apply(WorldBio w, IBot o, int x, int y) {
				if (o != null) {
					o.die(Ledger.DIE_REMOVED);
				}
			}
		},
		new Tool() {//сделать королевой (для проверки механики королев)
			public String name() { return "Make queen"; }
			public void apply(WorldBio w, IBot o, int x, int y) {
				if (o instanceof Bot bot && bot.state == 0 && bot.queen == 0) {
					bot.set_queen(bot, bot, 0);
					w.select(bot);
				}
			}
		},
		new Tool() {//подробности о клетке — в консоль
			public String name() { return "Info -> console"; }
			public void apply(WorldBio w, IBot o_cell, int x, int y) {
				Bot bot = o_cell instanceof Bot b ? b : null;
				System.out.printf("cell [%d, %d]: oxygen %.4f, co2 %.4f, organics %.1f, minerals %.1f%n",
					x, y, w.oxygen_map[x][y], w.co2_map[x][y], w.org_map[x][y], w.mnr_map[x][y]);
				IBot o = w.Map[x][y];
				if (o != null && !(o instanceof Bot)) {
					System.out.println("  " + o.describe());
				}
				if (bot != null) {
					System.out.printf("  bot: state %d, energy %.1f, age %.1f, type %d, rotate %d, queen %d, chains %d, genom %d, index %d, kin %d%n",
						bot.state, bot.energy, bot.age, bot.type, bot.rotate, bot.queen, bot.count_chains(), bot.genom_current_idx, bot.index, bot.kin_id);
					for (int g = 0; g < bot.genom_array.length; g++) {
						System.out.println("  genome " + g + ": " + java.util.Arrays.toString(bot.genom_array[g]));
					}
				}
			}
		},
	};
}
