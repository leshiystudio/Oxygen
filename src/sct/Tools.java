package sct;

import javax.swing.SwingUtilities;

//Инструменты правой кнопки мыши. Текущий выбирается на панели (или клавишами [ и ]),
//Esc сбрасывает инструмент на «Select» и снимает выделение.
//Новый инструмент — класс, реализующий Tool, добавленный в список all.
//apply вызывается под WorldBio.lock; bot — живой бот в клетке или null.
public class Tools {
	public interface Tool {
		String name();
		void apply(WorldBio w, Bot bot, int x, int y);
	}
	//
	public static final Tool[] all = {
		new Tool() {//выбрать бота; повторный щелчок по выбранному снимает выделение
			public String name() { return "Select"; }
			public void apply(WorldBio w, Bot bot, int x, int y) {
				w.select(bot == w.selection ? null : bot);
			}
		},
		new Tool() {//выбрать и открыть окно генома
			public String name() { return "Genome"; }
			public void apply(WorldBio w, Bot bot, int x, int y) {
				if (bot != null) {
					w.select(bot);
					SwingUtilities.invokeLater(() -> GenomeView.show(SwingUtilities.getWindowAncestor(w), w));
				}
			}
		},
		new Tool() {//убить бота: энергия и тело уходят в органику, как при обычной смерти
			public String name() { return "Remove"; }
			public void apply(WorldBio w, Bot bot, int x, int y) {
				if (bot != null) {
					bot.die_with_organics(Ledger.DIE_REMOVED);
				}
			}
		},
		new Tool() {//сделать королевой (для проверки механики королев)
			public String name() { return "Make queen"; }
			public void apply(WorldBio w, Bot bot, int x, int y) {
				if (bot != null && bot.state == 0 && bot.queen == 0) {
					bot.set_queen(bot, bot, 0);
					w.select(bot);
				}
			}
		},
		new Tool() {//подробности о клетке — в консоль
			public String name() { return "Info -> console"; }
			public void apply(WorldBio w, Bot bot, int x, int y) {
				System.out.printf("cell [%d, %d]: oxygen %.4f, co2 %.4f, organics %.1f, minerals %.1f%n",
					x, y, w.oxygen_map[x][y], w.co2_map[x][y], w.org_map[x][y], w.mnr_map[x][y]);
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
