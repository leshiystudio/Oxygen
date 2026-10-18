package sct;

import java.awt.Color;

//Расшифровка команд генома для просмотра (GenomeView). Должна совпадать с Bot.update_commands:
//при добавлении команды туда — добавить строку сюда.
public class Commands {
	//категории — цвет клетки в просмотре генома
	public static final int C_MOVE = 0, C_ENERGY = 1, C_ATTACK = 2, C_SENSE = 3, C_BIRTH = 4, C_SHARE = 5,
		C_CHAIN = 6, C_QUEEN = 7, C_GENOM = 8, C_JUMP = 9;
	public static final String[] category_names = {"move", "energy", "attack", "sense", "birth", "share", "chain", "queen", "genome", "jump"};
	public static final Color[] category_colors = {
		new Color(70, 110, 200), new Color(60, 160, 80), new Color(200, 60, 60), new Color(120, 100, 160),
		new Color(210, 130, 40), new Color(50, 160, 170), new Color(190, 170, 50), new Color(190, 70, 170),
		new Color(110, 110, 110), new Color(55, 55, 60)
	};
	//
	private static final String[] names = new String[64];
	private static final int[] categories = new int[64];
	static {
		for (int i = 0; i < 64; i++) {
			set(i, C_JUMP, "прыжок на " + i + " вперед");
		}
		set(0, C_MOVE, "повернуть на [p1] (относительно)");
		set(1, C_MOVE, "направление = [p1]");
		set(2, C_ENERGY, "фотосинтез");
		set(3, C_ENERGY, "фотосинтез");
		set(4, C_ENERGY, "фотосинтез");
		set(5, C_MOVE, "шаг в направлении [p1] (относительно)");
		set(6, C_MOVE, "шаг вперед");
		set(7, C_ATTACK, "атака в направлении [p1]");
		set(8, C_ATTACK, "атака вперед");
		set(9, C_SENSE, "смотреть в [p1]: переход по результату (семечко/пусто/свой/органика/чужой/королева/подчиненная)");
		set(10, C_SENSE, "смотреть вперед: переход по результату");
		set(11, C_SHARE, "отдать 1/4 энергии в [p1]");
		set(12, C_SHARE, "отдать 1/4 энергии в [p1]");
		set(13, C_SHARE, "отдать 1/4 энергии вперед");
		set(14, C_SHARE, "отдать 1/4 энергии вперед");
		set(15, C_SENSE, "энергия >= [p1]*15 ? переход [p2] : [p3]");
		set(16, C_JUMP, "свободно: прыжок на 16");
		set(17, C_JUMP, "свободно: прыжок на 17");
		set(18, C_BIRTH, "деление в [p1], тип потомка [p2]");
		set(19, C_BIRTH, "деление вперед, тип потомка [p1]");
		set(20, C_SENSE, "позиция x >= доля [p1] ? переход [p2] : [p3]");
		set(21, C_SENSE, "позиция y >= доля [p1] ? переход [p2] : [p3]");
		set(22, C_SENSE, "возраст >= [p1]*15 ? переход [p2] : [p3]");
		set(23, C_SHARE, "энергию поровну с соседом в [p1]");
		set(24, C_SHARE, "энергию поровну с соседом впереди");
		set(25, C_JUMP, "переход на [p1]");
		set(26, C_SENSE, "кислород >= порог [p1] ? переход [p2] : [p3]");
		set(27, C_ENERGY, "переработать органику под собой, [p1]");
		set(28, C_ENERGY, "переработать органику под собой, [p1]");
		set(29, C_ENERGY, "переработать органику в [p1], количество [p2]");
		set(30, C_ENERGY, "переработать органику в [p1], количество [p2]");
		set(31, C_ENERGY, "переработать органику впереди, [p1]");
		set(32, C_ENERGY, "переработать органику впереди, [p1]");
		set(33, C_SENSE, "органика подо мной >= [p1]*[p2] ? переход [p3] : [p4]");
		set(34, C_SENSE, "органика в [p1] >= [p2]*15 ? переход");
		set(35, C_SENSE, "органика впереди >= [p1]*15 ? переход");
		set(36, C_SENSE, "направление сравнить с [p1]: больше/равно/меньше");
		set(37, C_MOVE, "случайное направление");
		set(38, C_SENSE, "соседей сравнить с [p1]: больше/равно/меньше");
		set(39, C_BIRTH, "семечко в [p1], полет [p2], тип [p3]");
		set(40, C_BIRTH, "семечко вперед, полет [p1], тип [p2]");
		set(41, C_SENSE, "переход по своему типу");
		set(42, C_CHAIN, "деление в цепочку в [p1], тип [p2]");
		set(43, C_CHAIN, "деление в цепочку вперед, тип [p1]");
		set(44, C_SENSE, "связей сравнить с [p1]: больше/меньше/равно");
		set(45, C_CHAIN, "присоединить соседа в [p1] к цепочке");
		set(46, C_CHAIN, "присоединить соседа впереди к цепочке");
		set(47, C_GENOM, "сменить геном на [p1]");
		set(48, C_GENOM, "переход по номеру текущего генома");
		set(49, C_QUEEN, "подчиненная: призвать соседей к своей королеве");
		set(50, C_QUEEN, "стать королевой (>= 5 связей) ? переход [p1] : [p2]");
		set(51, C_QUEEN, "статус королевы == [p1] ? переход [p2] : [p3]");
		set(52, C_QUEEN, "королева: приказ [p1] подчиненным на дистанции [p2]");
		set(53, C_QUEEN, "королева: приказ [p1] подчиненным дальше [p2]");
		set(54, C_QUEEN, "королева: приказ [p1] подчиненным ближе [p2]");
		set(55, C_QUEEN, "королева: посчитать подчиненных (заглушка)");
	}
	private static void set(int i, int category, String name) {
		names[i] = name;
		categories[i] = category;
	}
	//
	public static String name(int command) {
		return command >= 0 && command < names.length ? names[command] : "прыжок на " + command;
	}
	public static int category(int command) {
		return command >= 0 && command < categories.length ? categories[command] : C_JUMP;
	}
}
