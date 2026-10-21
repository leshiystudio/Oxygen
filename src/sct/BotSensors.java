package sct;

//Датчики тела бота Oxygen — для нейросети и своих алгоритмов.
//Значения сырые: энергия как есть, see() — код (0 семечко, 1 пусто, 2 свой, 3 органика,
//4 чужой, 5 королева, 6 подчиненная, 7 растение, 8 нейро-бот).
public class BotSensors implements Sensors<Bot> {
	public static final BotSensors instance = new BotSensors();
	private static final String[] names = {
		"energy", "age", "oxygen", "co2", "organics under", "organics forward",
		"see forward", "see left", "see right", "neighbours", "chains",
		"queen status", "has order", "x", "y"
	};
	public String[] names() {
		return names;
	}
	public void read(Bot b, float[] out) {
		int[] fwd = b.get_rotate_position(b.rotate);
		out[0] = (float) b.energy;
		out[1] = (float) b.age;
		out[2] = (float) b.oxygen();
		out[3] = (float) b.co2();
		out[4] = (float) b.organics(b.xpos, b.ypos);
		out[5] = (float) b.organics(fwd[0], fwd[1]);
		out[6] = b.see(b.rotate);
		out[7] = b.see((b.rotate + 6) % 8);
		out[8] = b.see((b.rotate + 2) % 8);
		out[9] = b.bot_count();
		out[10] = b.count_chains();
		out[11] = b.queen;
		out[12] = b.queen_command >= 0 ? 1 : 0;
		out[13] = b.xpos;
		out[14] = b.ypos;
	}
}
