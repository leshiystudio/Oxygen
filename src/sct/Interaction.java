package sct;

//Таблица взаимодействий популяций: [кто][с кем]. Внутри одной популяции действуют правила ее вида
//как есть (бот Oxygen — свои, растение — biosim); таблица работает только между разными популяциями.
//
//  eat[a][b]       — КПД, с которым a ест b (0 — нельзя). Остаток (1 - КПД) не пропадает:
//                    уходит в органику (бот) или почву (растение) по правилам жертвы.
//  exchange[a][b]  — коэффициент перевода энергии b в энергию a при поедании (регулировка баланса).
//                    exchange != 1 создает или уничтожает энергию — отдельная статья журнала.
//  share[a][b]     — a может передавать энергию b (give, «поровну»).
//  chain[a][b]     — a может связываться цепочкой с b (и королевы через нее).
//  seed_kills[a][b] — семечко a при столкновении убивает b (иначе гибнет только семечко).
public class Interaction {
	public final double[][] eat;
	public final double[][] exchange;
	public final boolean[][] share;
	public final boolean[][] chain;
	public final boolean[][] seed_kills;

	public Interaction(int n) {
		eat = new double[n][n];
		exchange = new double[n][n];
		share = new boolean[n][n];
		chain = new boolean[n][n];
		seed_kills = new boolean[n][n];
		for (int a = 0; a < n; a++) {
			for (int b = 0; b < n; b++) {
				eat[a][b] = 1.0;//по умолчанию разрешено все
				exchange[a][b] = 1.0;
				share[a][b] = a == b;//передача и цепочки — только внутри популяции
				chain[a][b] = a == b;
				seed_kills[a][b] = true;//как сейчас у ботов Oxygen
			}
		}
	}
	//
	public static Interaction current = new Interaction(0);//таблица текущего мира (WorldBio.new_population)
}
