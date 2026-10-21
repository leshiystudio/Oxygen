package sct.neuro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.ListIterator;
import java.util.Map;

import sct.Bot;
import sct.Brain;
import sct.Constant;
import sct.IBot;

//Мозг мухи: модель всего мозга дрозофилы (FlyWire, 139 тыс. нейронов) на видеокарте, сервис flybrain/server.py.
//Тело — то же, что у нейро-бота (BotBody): его датчики уходят в сервис, обратно приходит действие.
//Какие датчики идут в какие нейроны и какие нейроны голосуют за какие действия — flybrain/mapping.yaml.
//
//За каждым живым ботом-мухой закреплен слот — свое состояние нейронов на видеокарте. Раз в ход мира
//(первая муха, которой нечего делать) датчики всех мух уходят одной пачкой; мир ждет ответа.
//Режим (Constant.fly_mode): single — мухи не размножаются, слотов столько, сколько мух в популяции;
//learn — размножаются, пока есть свободный из fly_slots слотов.
public class FlyBrain implements Brain<Bot> {
	private static final Hub hub = new Hub();

	public FlyBrain(int count) {//прототип популяции: новый мир — слоты заново
		hub.new_world(Constant.fly_mode.equals("single") ? Math.max(1, count) : Math.max(1, Constant.fly_slots));
	}
	public String name() {
		return "fly";
	}
	public boolean breathes() {
		return true;
	}
	public void died(Bot b, int cause) {
		hub.died(b, cause);
	}
	public boolean can_multiply(Bot parent) {
		return !Constant.fly_mode.equals("single") && hub.has_free_slot();
	}
	public void think(Bot b, ListIterator<IBot> it) {
		String[] d = hub.decide(b);
		if (d == null || d[0].equals("-")) {
			return;
		}
		BotBody body = BotBody.instance;
		String[] acts = body.actions();
		int a = 0;
		while (a < acts.length && !acts[a].equals(d[0])) {
			a++;
		}
		if (a == acts.length) {
			return;
		}
		String[] p = d[2].split(",");
		float[] params = {Float.parseFloat(p[0]), Float.parseFloat(p[1])};
		body.act(b, it, a, dir(b, acts[a], d[1]), params);
	}
	private static int dir(Bot b, String action, String d) {//forward/back/left/right — от взгляда бота, число — абсолютное
		int off;
		switch (d) {
			case "forward": off = 0; break;
			case "right": off = 2; break;
			case "back": off = 4; break;
			case "left": off = 6; break;
			default: return Integer.parseInt(d) % 8;
		}
		return action.equals("turn") ? off : (b.rotate + off) % 8;//turn: направление — угол поворота
	}
	public static String rates(Bot b) {//частоты групп нейронов мухи, например для окна существа
		return hub.rates(b);
	}
	//
	private static class Hub {
		private Socket socket;
		private BufferedReader in;
		private PrintWriter out;
		private Process server;
		private Bot[] owner = new Bot[0];
		private String[][] decision = new String[0][];
		private final Map<Bot, Integer> slot_of = new IdentityHashMap<Bot, Integer>();
		private Map<String, Integer>[] done;//сколько раз муха слота выбрала каждое действие (итог — при гибели)
		private int lives;//сколько мух прожило жизнь (все слоты, с запуска программы)
		private int diets, recyclers;//сколько мухам выдано типов питания и из них едоков органики (с начала мира)
		private boolean warned;

		synchronized void new_world(int slots) {
			owner = new Bot[slots];
			decision = new String[slots][];
			done = new java.util.TreeMap[slots];
			for (int i = 0; i < slots; i++) {
				done[i] = new java.util.TreeMap<String, Integer>();
			}
			slot_of.clear();
			warned = false;
			diets = 0;
			recyclers = 0;
			connect();
			BotBody body = BotBody.instance;
			String reply = call("hello " + slots + "\t" + String.join("|", body.inputs()) + "\t" + String.join("|", body.actions()));
			String[] r = reply.substring(3).split("\t", 2);
			int rec = Integer.parseInt(r[0]);
			System.out.println("муха: " + r[1] + (rec > 0 ? "; рекомендуется не больше " + rec + " мух (fly_slots)" : ""));
			if (rec > 0 && slots > rec) {
				System.out.println("муха: мух больше рекомендуемого — мир пойдет медленнее 20 ходов в секунду");
			}
		}
		synchronized boolean has_free_slot() {
			sweep();
			for (Bot o : owner) {
				if (o == null) {
					return true;
				}
			}
			return false;
		}
		synchronized String[] decide(Bot b) {
			Integer s = slot_of.get(b);
			if (s == null) {
				s = take_slot(b);
				if (s == null) {
					if (!warned) {
						System.out.println("муха: свободных слотов нет — лишние мухи стоят на месте");
						warned = true;
					}
					return null;
				}
			}
			if (decision[s] == null) {
				step();
			}
			String[] d = decision[s];
			decision[s] = null;
			if (d != null) {
				done[s].merge(d[0].equals("-") ? "-" : d[0] + " " + d[1], 1, Integer::sum);
			}
			return d;
		}
		synchronized String rates(Bot b) {
			Integer s = slot_of.get(b);
			return s == null ? "" : call("rates " + s).substring(6);
		}
		private Integer take_slot(Bot b) {
			sweep();
			for (int i = 0; i < owner.length; i++) {
				if (owner[i] == null) {
					owner[i] = b;
					slot_of.put(b, i);
					call("reset " + i);
					assign_diet(b);
					return i;
				}
			}
			return null;
		}
		private void assign_diet(Bot b) {//тип питания новой мухи: доля едоков органики — fly_recyclers, без случайности
			if (b.pht_org_block == 0) {
				boolean recycler = recyclers < Math.round(Constant.fly_recyclers * (diets + 1));
				b.pht_org_block = recycler ? 2 : 1;
			}
			diets++;
			if (b.pht_org_block == 2) {
				recyclers++;
			}
		}
		private boolean diet_allows(Bot b, String action) {//фотосинтетику не есть органику, едоку — не фотосинтез
			return !(action.equals("photo") && b.pht_org_block == 2 || action.equals("recycle") && b.pht_org_block == 1);
		}
		private static final String[] CAUSES = {"другое", "старость", "голод", "органика", "съедена", "столкновение с семечком"};
		synchronized void died(Bot b, int cause) {
			Integer s = slot_of.remove(b);
			if (s != null) {
				report(s, cause >= 0 && cause < CAUSES.length ? CAUSES[cause] : "причина " + cause);
				owner[s] = null;
				decision[s] = null;
			}
		}
		private void sweep() {//слоты погибших мух — свободны (на случай смерти мимо died)
			for (int i = 0; i < owner.length; i++) {
				if (owner[i] != null && owner[i].killed == 1) {
					report(i, "?");
					slot_of.remove(owner[i]);
					owner[i] = null;
					decision[i] = null;
				}
			}
		}
		private void report(int s, String cause) {//итог жизни мухи: сколько ходов, отчего погибла, чем занималась
			int total = 0;
			for (int c : done[s].values()) {
				total += c;
			}
			String diet = owner[s] == null ? "?" : owner[s].pht_org_block == 2 ? "органика" : "фотосинтез";
			StringBuilder sb = new StringBuilder("муха (слот " + s + ", жизнь " + (lives + 1) + ", питание: " + diet + "): прожила "
				+ total + " ходов, погибла: " + cause + ";");
			for (Map.Entry<String, Integer> e : done[s].entrySet()) {
				sb.append(' ').append(e.getKey()).append(' ').append(Math.round(100.0 * e.getValue() / Math.max(1, total))).append('%');
			}
			System.out.println(sb);
			lives++;
			done[s].clear();
		}
		private void step() {
			sweep();
			BotBody body = BotBody.instance;
			int n_in = body.inputs().length;
			int n_act = body.actions().length;
			float[] v = new float[n_in];
			StringBuilder sb = new StringBuilder("step ");
			for (int i = 0; i < owner.length; i++) {
				Bot o = owner[i];
				if (o == null) {
					continue;
				}
				body.read(o, v);
				sb.append(i).append(':');
				for (int a = 0; a < n_act; a++) {
					sb.append(body.enabled(o, a) && diet_allows(o, body.actions()[a]) ? '1' : '0');
				}
				sb.append(':');
				for (int k = 0; k < n_in; k++) {
					if (k > 0) {
						sb.append(',');
					}
					sb.append(v[k]);
				}
				sb.append(';');
			}
			String reply = call(sb.toString());
			for (String part : reply.substring(5).split(";")) {
				if (part.isEmpty()) {
					continue;
				}
				String[] f = part.split(":");
				decision[Integer.parseInt(f[0])] = new String[]{f[1], f[2], f[3]};
			}
		}
		private String call(String line) {
			out.print(line + "\n");//не println: под Windows он пишет \r\n
			out.flush();
			try {
				String r = in.readLine();
				if (r == null) {
					throw new IllegalStateException("сервис мозга мухи закрыл соединение");
				}
				if (r.startsWith("error ")) {
					throw new IllegalStateException("мозг мухи: " + r.substring(6));
				}
				return r;
			}catch (IOException e) {
				throw new IllegalStateException("мозг мухи: " + e.getMessage(), e);
			}
		}
		private static String host() {//auto — из окружения (так Docker-сервер не зависит от настроек, присланных пультом)
			if (!Constant.fly_host.equals("auto")) {
				return Constant.fly_host;
			}
			String env = System.getenv("OXYGEN_FLY_HOST");
			return env == null || env.isBlank() ? "127.0.0.1" : env.trim();
		}
		private void connect() {//к уже запущенному сервису, иначе (сервис на этой машине) запустить его самим
			if (socket != null && !socket.isClosed()) {
				return;
			}
			if (try_connect()) {
				return;
			}
			String where = host() + ":" + Constant.fly_port;
			if (!host().equals("127.0.0.1") && !host().equals("localhost")) {
				throw new IllegalStateException("муха: нет сервиса мозга на " + where + ". Запустите его на машине с видеокартой: "
					+ "python flybrain/server.py --host 0.0.0.0 (flybrain/README.md, раздел про Docker)");
			}
			try {
				System.out.println("муха: запускаю " + Constant.fly_python + " flybrain/server.py (загрузка мозга — несколько секунд)");
				server = new ProcessBuilder(Constant.fly_python, "flybrain/server.py", "--port", String.valueOf(Constant.fly_port))
					.inheritIO().start();
				Runtime.getRuntime().addShutdownHook(new Thread(server::destroy));
			}catch (IOException e) {
				throw new IllegalStateException("муха: нет сервиса мозга на " + where + " и не запустить его (fly_python=" + Constant.fly_python
					+ "): " + e.getMessage() + ". Запустите сами: python flybrain/server.py", e);
			}
			long until = System.currentTimeMillis() + 180_000;
			while (System.currentTimeMillis() < until) {
				if (!server.isAlive()) {
					throw new IllegalStateException("муха: сервис мозга завершился с кодом " + server.exitValue() + " — его вывод выше");
				}
				if (try_connect()) {
					return;
				}
				try {
					Thread.sleep(500);
				}catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			throw new IllegalStateException("муха: сервис мозга не ответил на порту " + Constant.fly_port);
		}
		private boolean try_connect() {
			try {
				socket = new Socket();
				socket.connect(new java.net.InetSocketAddress(host(), Constant.fly_port), 3000);
				socket.setTcpNoDelay(true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
				return true;
			}catch (IOException e) {
				socket = null;
				return false;
			}
		}
	}
}
