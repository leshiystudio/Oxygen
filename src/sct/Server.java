package sct;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

//Мир без окна для сервера: симуляция идет на полной скорости, кадр рисуется только по запросу зрителя.
//  java -Djava.awt.headless=true -cp out sct.Server [--view-port 8080] [--control-port 8081] [--token T]
//       [--resume] [--load файл.oxw] [--settings файл.properties] [--autosave минут] [--frame-ms 1000] [--brain-limit 10]
//       [--trust-proxy] [--view-rps 10] [--view-burst 30] [--streams-per-ip 6] [--max-streams 500]
//       [--stats-every 10] [--kin-every 500] [--preset ... --set ...]
//Порт просмотра — только чтение: страница, кадр, статус, графики. Порт управления — то же плюс
//пауза, скорость, новый мир по настройкам (.properties как у диалога «Новый мир»), сохранение и загрузка.
//Токен управления — --token или переменная OXYGEN_CONTROL_TOKEN; без токена управление открыто всем, кто достучится до порта.
//Остальные аргументы — как у Main (см. Presets). Файлы — в «saved worlds» текущего каталога.
public class Server {
	static final File dir = new File("saved worlds");
	static final String autosave_name = "autosave.oxw";
	static WorldBio w;
	static String token;
	static volatile double steps_per_sec = 0;
	static int stats_every = 10;//точка истории и сверка журнала энергии — раз в столько шагов
	static int kin_every = 500;//пересчет кланов по геному — раз в столько шагов
	//
	public static void main(String[] args) throws IOException {
		int view_port = 8080, control_port = 8081, autosave_min = 0;
		boolean resume = false;
		String load = null, settings = null;
		token = System.getenv("OXYGEN_CONTROL_TOKEN");
		trust_proxy = "1".equals(System.getenv("OXYGEN_TRUST_PROXY")) || "true".equalsIgnoreCase(System.getenv("OXYGEN_TRUST_PROXY"));
		List<String> rest = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--view-port": view_port = Integer.parseInt(args[++i]); break;
				case "--control-port": control_port = Integer.parseInt(args[++i]); break;
				case "--token": token = args[++i]; break;
				case "--autosave": autosave_min = Integer.parseInt(args[++i]); break;
				case "--frame-ms": frame_ms = Math.max(min_frame_ms, Long.parseLong(args[++i])); break;
				case "--brain-limit": brain_limit_sec = Math.max(0, Integer.parseInt(args[++i])); break;
				case "--trust-proxy": trust_proxy = true; break;
				case "--view-rps": view_rps = Math.max(0, Double.parseDouble(args[++i])); break;
				case "--view-burst": view_burst = Math.max(1, Integer.parseInt(args[++i])); break;
				case "--streams-per-ip": streams_per_ip = Math.max(0, Integer.parseInt(args[++i])); break;
				case "--max-streams": max_streams = Math.max(0, Integer.parseInt(args[++i])); break;
				case "--resume": resume = true; break;
				case "--load": load = args[++i]; break;
				case "--settings": settings = args[++i]; break;
				case "--stats-every": stats_every = Math.max(1, Integer.parseInt(args[++i])); break;
				case "--kin-every": kin_every = Math.max(1, Integer.parseInt(args[++i])); break;
				default: rest.add(args[i]);
			}
		}
		//метрики и кланы — полные проходы по миру; на сервере реже, чем в окне: шаг быстрее
		History.every = stats_every;
		Ledger.check_every = stats_every;
		KinClans.update_every = kin_every;
		if (token != null && token.isBlank()) {
			token = null;
		}
		Presets.apply_args(rest.toArray(new String[0]));
		WorldSettings.startup = WorldSettings.capture();
		dir.mkdirs();
		w = new WorldBio(false);
		File auto = new File(dir, autosave_name);
		if (load == null && resume && auto.isFile()) {
			load = auto.getPath();
		}
		if (load != null) {
			WorldIO.load(w, find(load));
			log("загружен мир " + load + ", шаг " + w.steps);
		}else if (settings != null) {
			WorldSettings s = WorldSettings.load(find(settings));
			s.apply(w);
			w.last_settings = s;
			log("мир по настройкам " + settings);
		}
		w.start_simulation();
		//
		HttpServer view = HttpServer.create(new InetSocketAddress(view_port), 0);
		route(view, false);
		view.setExecutor(Executors.newVirtualThreadPerTaskExecutor());//долгие соединения потока кадров дешевы; число запросов держит лимит по IP
		view.start();
		if (control_port > 0) {
			HttpServer control = HttpServer.create(new InetSocketAddress(control_port), 0);
			route(control, true);
			control.setExecutor(Executors.newFixedThreadPool(2));
			control.start();
		}
		if (trust_proxy) {
			log("адрес зрителя — из X-Forwarded-For / X-Real-IP прокси во внутренней сети (проверка: /api/whoami)");
		}
		log("просмотр: порт " + view_port + ", управление: " + (control_port > 0 ? "порт " + control_port : "выключено")
			+ (control_port > 0 && token == null ? " (без токена — откройте порт только для своих)" : ""));
		//
		Thread speed = new Thread(Server::measure_speed, "speed");
		speed.setDaemon(true);
		speed.start();
		if (autosave_min > 0) {
			long period = autosave_min * 60_000L;
			Thread t = new Thread(() -> {
				while (true) {
					try {
						Thread.sleep(period);
					}catch (InterruptedException e) {
						return;
					}
					autosave();
				}
			}, "autosave");
			t.setDaemon(true);
			t.start();
			Runtime.getRuntime().addShutdownHook(new Thread(Server::autosave, "autosave-on-exit"));//docker stop — мир не теряется
		}
	}
	//
	private static File find(String name) {//путь или просто имя файла в «saved worlds» (в OXYGEN_ARGS путь с пробелом не передать)
		File f = new File(name);
		return f.isFile() ? f : new File(dir, name);
	}
	static void log(String msg) {
		System.out.println(java.time.LocalDateTime.now().withNano(0) + "  " + msg);
	}
	private static void measure_speed() {//шагов в секунду — для статуса
		int last = w.steps;
		int last_renders = 0;
		while (true) {
			try {
				Thread.sleep(1000);
			}catch (InterruptedException e) {
				return;
			}
			int now = w.steps;
			int d = now >= last ? now - last : now;//мир пересоздан — счет с нуля
			steps_per_sec = steps_per_sec == 0 ? d : steps_per_sec * 0.7 + d * 0.3;
			last = now;
			int r = renders;
			renders_per_sec = renders_per_sec * 0.7 + (r - last_renders) * 0.3;
			last_renders = r;
		}
	}
	static synchronized void autosave() {//сначала во временный файл: оборванная запись не портит прошлое сохранение
		File tmp = new File(dir, autosave_name + ".tmp");
		try {
			WorldIO.save(w, tmp);
			Files.move(tmp.toPath(), new File(dir, autosave_name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			log("автосохранение, шаг " + w.steps);
		}catch (IOException e) {
			log("автосохранение не удалось: " + e.getMessage());
		}
	}
	//
	//МАРШРУТЫ
	//
	interface Handler {
		void handle(HttpExchange ex, Map<String, String> q) throws IOException;
	}
	private static void route(HttpServer srv, boolean control) {
		String page = control ? "control.html" : "view.html";
		on(srv, "/", control, (ex, q) -> {
			if (!ex.getRequestURI().getPath().equals("/")) {
				send(ex, 404, "text/plain", "not found");
			}else {
				send_resource(ex, page);
			}
		});
		on(srv, "/view", control, (ex, q) -> send_resource(ex, "view.html"));
		on(srv, "/frame.png", control, Server::frame);
		on(srv, "/stream", control, Server::stream);
		on(srv, "/api/status", control, (ex, q) -> json(ex, status()));
		on(srv, "/api/history", control, Server::history);
		on(srv, "/api/brain", control, (ex, q) -> brain(ex, q, control));
		on(srv, "/api/whoami", control, (ex, q) -> json(ex, "{\"client\":" + str(ip(ex))//каким адресом сервер считает зрителя (лимиты — по нему)
			+ ",\"peer\":" + str(ex.getRemoteAddress().getAddress().getHostAddress())
			+ ",\"x_forwarded_for\":" + str(ex.getRequestHeaders().getFirst("X-Forwarded-For"))
			+ ",\"x_real_ip\":" + str(ex.getRequestHeaders().getFirst("X-Real-IP"))
			+ ",\"trust_proxy\":" + trust_proxy + "}"));
		on(srv, "/api/history.csv", control, (ex, q) -> {
			String csv;
			w.lock.lock();
			try {
				csv = History.csv();
			}finally {
				w.lock.unlock();
			}
			ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"history_" + w.steps + ".csv\"");
			send(ex, 200, "text/csv; charset=utf-8", csv);
		});
		if (!control) {
			return;
		}
		on(srv, "/api/pause", true, post((ex, q) -> { w.pause = true; ok(ex); }));
		on(srv, "/api/resume", true, post((ex, q) -> { w.pause = false; ok(ex); }));
		on(srv, "/api/step", true, post((ex, q) -> { w.pause = true; w.request_step(); ok(ex); }));
		on(srv, "/api/speed", true, post((ex, q) -> {
			if (q.containsKey("level")) {
				w.speed_idx = Math.max(0, Math.min(WorldBio.speed_levels.length - 1, Integer.parseInt(q.get("level"))));
			}else if ("-1".equals(q.get("delta"))) {
				w.slower();
			}else {
				w.faster();
			}
			ok(ex);
		}));
		on(srv, "/api/frame", true, post((ex, q) -> {//интервал кадров для всех зрителей
			frame_ms = Math.max(min_frame_ms, Long.parseLong(q.getOrDefault("ms", "1000")));
			log("кадр раз в " + frame_ms + " мс");
			ok(ex);
		}));
		on(srv, "/api/view-limit", true, post((ex, q) -> {//запросов в секунду с адреса на порту просмотра (0 — без ограничения)
			view_rps = Math.max(0, Double.parseDouble(q.getOrDefault("rps", "10")));
			if (q.containsKey("burst")) {
				view_burst = Math.max(1, Integer.parseInt(q.get("burst")));
			}
			synchronized (buckets) {
				buckets.clear();
			}
			log("просмотр — не больше " + view_rps + " запросов/с с адреса (всплеск " + view_burst + ")");
			ok(ex);
		}));
		on(srv, "/api/brain-limit", true, post((ex, q) -> {//как часто зритель может запросить мозг существа
			brain_limit_sec = Math.max(0, Integer.parseInt(q.getOrDefault("sec", "10")));
			synchronized (brain_last) {
				brain_last.clear();
			}
			log("мозг существа — не чаще раза в " + brain_limit_sec + " с на зрителя");
			ok(ex);
		}));
		on(srv, "/api/again", true, post((ex, q) -> { w.newPopulation(); log("новый мир с теми же настройками"); ok(ex); }));
		on(srv, "/api/settings", true, (ex, q) -> {//настройки текущего мира — .properties
			String text;
			w.lock.lock();
			try {
				text = WorldSettings.of(w).to_text();
			}finally {
				w.lock.unlock();
			}
			send(ex, 200, "text/plain; charset=utf-8", text);
		});
		on(srv, "/api/schema", true, (ex, q) -> json(ex, schema()));
		on(srv, "/api/settings.json", true, (ex, q) -> {//from=current (идущий мир, по умолчанию) или startup (кнопка Defaults)
			WorldSettings s;
			if ("startup".equals(q.get("from"))) {
				s = WorldSettings.startup.copy();
			}else {
				w.lock.lock();
				try {
					s = WorldSettings.of(w);
				}finally {
					w.lock.unlock();
				}
			}
			json(ex, settings_json(s));
		});
		on(srv, "/api/settings/parse", true, post((ex, q) -> json(ex, settings_json(WorldSettings.from_text(body(ex))))));//.properties -> JSON для редактора
		on(srv, "/api/world/new", true, post((ex, q) -> {//тело — .properties; предупреждение проверки — 409, пока не force=1
			WorldSettings s = WorldSettings.from_text(body(ex));
			if (s.populations.isEmpty()) {//пустое тело запроса — не «мир без жизни», а ошибка клиента
				throw new IllegalArgumentException("в настройках нет ни одной популяции");
			}
			String warning = s.check();
			if (warning != null && !"1".equals(q.get("force"))) {
				json(ex, 409, "{\"ok\":false,\"warning\":" + str(warning) + "}");
				return;
			}
			try {
				s.copy().apply(w);
			}catch (IllegalArgumentException e) {
				json(ex, 400, "{\"ok\":false,\"error\":" + str(e.getMessage()) + "}");
				return;
			}
			w.last_settings = s;
			log("новый мир по настройкам с порта управления");
			ok(ex);
		}));
		on(srv, "/api/files", true, (ex, q) -> {
			StringBuilder sb = new StringBuilder("[");
			File[] files = dir.listFiles();
			if (files != null) {
				java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
				for (File f : files) {
					if (f.isFile() && allowed(f.getName())) {
						sb.append(sb.length() > 1 ? "," : "").append("{\"name\":").append(str(f.getName()))
							.append(",\"size\":").append(f.length()).append(",\"modified\":").append(f.lastModified()).append("}");
					}
				}
			}
			json(ex, sb.append("]").toString());
		});
		on(srv, "/api/save", true, post((ex, q) -> {
			File f = file(q.get("name"));
			if (f.getName().endsWith(".properties")) {
				Files.writeString(f.toPath(), body(ex), StandardCharsets.UTF_8);//настройки из редактора
			}else {
				WorldIO.save(w, f);
			}
			log("сохранено " + f.getName());
			ok(ex);
		}));
		on(srv, "/api/load", true, post((ex, q) -> {
			File f = file(q.get("name"));
			WorldIO.load(w, f);
			log("загружен мир " + f.getName() + ", шаг " + w.steps);
			ok(ex);
		}));
		on(srv, "/api/download", true, (ex, q) -> {
			File f = file(q.get("name"));
			if (!f.isFile()) {
				send(ex, 404, "text/plain", "нет файла");
				return;
			}
			ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
			send(ex, 200, f.getName().endsWith(".properties") ? "text/plain; charset=utf-8" : "application/octet-stream", Files.readAllBytes(f.toPath()));
		});
		on(srv, "/api/delete", true, post((ex, q) -> {
			File f = file(q.get("name"));
			Files.deleteIfExists(f.toPath());
			log("удалено " + f.getName());
			ok(ex);
		}));
	}
	private static void on(HttpServer srv, String path, boolean control, Handler h) {
		srv.createContext(path, ex -> {
			try {
				if (control && !authorized(ex)) {
					ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Oxygen control\", charset=\"UTF-8\"");
					send(ex, 401, "text/plain", "нужен токен управления");
					return;
				}
				if (!control && !allow(ip(ex))) {//порт просмотра: не больше view_rps запросов в секунду с адреса
					ex.getResponseHeaders().set("Retry-After", "1");
					json(ex, 429, "{\"ok\":false,\"error\":\"слишком много запросов\"}");
					return;
				}
				h.handle(ex, query(ex));
			}catch (IllegalArgumentException | IOException e) {
				json(ex, 400, "{\"ok\":false,\"error\":" + str(String.valueOf(e.getMessage())) + "}");
			}catch (RuntimeException e) {
				e.printStackTrace();
				json(ex, 500, "{\"ok\":false,\"error\":" + str(e.toString()) + "}");
			}finally {
				ex.close();
			}
		});
	}
	//
	//ЛИМИТ ЗАПРОСОВ ПО АДРЕСУ (порт просмотра): корзина токенов — в среднем view_rps в секунду, всплеск до view_burst.
	//Защищает поток симуляции (status, history берут блокировку мира) и канал (кадр — десятки КБ).
	//Порт управления не ограничен. За обратным прокси все зрители — один адрес: тогда лимит лучше ставить на прокси.
	//
	static volatile double view_rps = 10;
	static volatile int view_burst = 30;
	private static final Map<String, double[]> buckets = new HashMap<String, double[]>();//адрес -> {токены, когда пополнено}
	//За туннелем или прокси (CloudPub, nginx) все зрители приходят с адреса прокси — лимиты стали бы общими.
	//--trust-proxy / OXYGEN_TRUST_PROXY=1: если соединение пришло из внутренней сети (docker, localhost), адрес
	//зрителя — самый правый публичный в X-Forwarded-For (правее — внутренние узлы туннеля, левее — что прислал
	//сам зритель, ему не верим) или X-Real-IP. Снаружи заголовкам не верим вовсе: иначе любой обошел бы лимит,
	//подставив чужой адрес. Проверка — GET /api/whoami.
	static volatile boolean trust_proxy = false;
	static String ip(HttpExchange ex) {
		java.net.InetAddress peer = ex.getRemoteAddress().getAddress();
		if (trust_proxy && (peer.isLoopbackAddress() || peer.isSiteLocalAddress() || peer.isLinkLocalAddress())) {
			String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
			if (xff != null && !xff.isBlank()) {
				String[] hops = xff.split(",");
				for (int i = hops.length - 1; i >= 0; i--) {
					String hop = hops[i].trim();
					if (is_public(hop)) {
						return hop;
					}
				}
			}
			String real = ex.getRequestHeaders().getFirst("X-Real-IP");
			if (real != null && !real.isBlank()) {
				return real.trim();
			}
		}
		return peer.getHostAddress();
	}
	private static boolean is_public(String ip) {//только адрес цифрами: имя не разрешаем (DNS на каждый запрос)
		if (ip.isEmpty() || !ip.matches("[0-9a-fA-F:.]+")) {
			return false;
		}
		try {
			java.net.InetAddress a = java.net.InetAddress.getByName(ip);
			return !(a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress());
		}catch (java.net.UnknownHostException e) {
			return false;
		}
	}
	static boolean allow(String ip) {
		if (view_rps <= 0) {
			return true;
		}
		long now = System.nanoTime();
		synchronized (buckets) {
			if (buckets.size() > 20_000) {//давно молчащие адреса (корзина полна) — вон
				buckets.values().removeIf(b -> b[0] + (now - b[1]) / 1e9 * view_rps >= view_burst);
			}
			double[] b = buckets.computeIfAbsent(ip, k -> new double[]{view_burst, now});
			b[0] = Math.min(view_burst, b[0] + (now - b[1]) / 1e9 * view_rps);
			b[1] = now;
			if (b[0] < 1) {
				return false;
			}
			b[0] -= 1;
			return true;
		}
	}
	//изменения — только POST с заголовком X-Oxygen: форма или картинка с чужого сайта его не пришлет (защита от CSRF)
	private static Handler post(Handler h) {
		return (ex, q) -> {
			if (!ex.getRequestMethod().equals("POST") || ex.getRequestHeaders().getFirst("X-Oxygen") == null) {
				send(ex, 405, "text/plain", "нужен POST с заголовком X-Oxygen");
				return;
			}
			h.handle(ex, q);
		};
	}
	private static boolean authorized(HttpExchange ex) {//Basic (пароль — токен, имя любое) или Bearer
		if (token == null) {
			return true;
		}
		String a = ex.getRequestHeaders().getFirst("Authorization");
		if (a == null) {
			return false;
		}
		String given = null;
		if (a.startsWith("Bearer ")) {
			given = a.substring(7).trim();
		}else if (a.startsWith("Basic ")) {
			try {
				String up = new String(Base64.getDecoder().decode(a.substring(6).trim()), StandardCharsets.UTF_8);
				given = up.substring(up.indexOf(':') + 1);
			}catch (IllegalArgumentException e) {
				return false;
			}
		}
		return given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
	}
	private static boolean allowed(String name) {
		return name.matches("[\\p{L}\\p{N}._ -]{1,120}") && !name.startsWith(".")
			&& (name.endsWith(".oxw") || name.endsWith(".properties") || name.endsWith(".csv"));
	}
	private static File file(String name) {//только файлы в «saved worlds», без путей
		if (name == null || !allowed(name)) {
			throw new IllegalArgumentException("недопустимое имя файла (буквы, цифры, . _ - и пробел; .oxw, .properties, .csv)");
		}
		return new File(dir, name);
	}
	//
	//КАДР: рисуется по запросу и по общим часам сервера. Время делится на отрезки по frame_ms;
	//кадр слоя (боты, фон, клетка) рисуется не больше раза за отрезок — сколько бы зрителей ни спрашивали
	//и как бы часто ни спрашивали: частоту задает сервер (--frame-ms, порт управления), не зритель.
	//Зритель получает время и шаг кадра и когда будет следующий; кадр, который у него уже есть, — 304 без тела.
	//Мир стоит (пауза) — кадр не перерисовывается, только переходит в новый отрезок.
	//Рисует один PixelRenderer по очереди: в его буфер, потом PNG; памяти — один буфер, а не по буферу на слой.
	//
	static final long min_frame_ms = 200;
	static volatile long frame_ms = 1000;
	private static final PixelRenderer renderer = new PixelRenderer();
	private static final int frame_cache_size = 32;//комбинаций; дольше всех не запрошенная — вон
	private record Frame(long tick, int step, long time, String etag, byte[] png) {}
	private static final Map<String, Frame> frames = new java.util.LinkedHashMap<String, Frame>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;
		protected boolean removeEldestEntry(Map.Entry<String, Frame> e) {
			return size() > frame_cache_size;
		}
	};
	static volatile int renders = 0;//сколько кадров нарисовано — для статуса (renders_per_sec)
	static volatile double renders_per_sec = 0;
	//размер клетки: при 1 и 2 px линии направления нет ((s - 1) / 2 = 0) и семечко — вся клетка, то есть кадр 2 px —
	//тот же кадр 1 px, растянутый вдвое; растягивает браузер, а рисуем и шлем вчетверо меньше пикселей
	private static int scale(Map<String, String> q) {
		int scale = clamp(q.get("scale"), 1, 1, Math.max(Constant.size, 8));
		return scale == 2 ? 1 : scale;
	}
	private static Frame frame_for(Map<String, String> q) throws IOException {
		int mode = clamp(q.get("mode"), 0, 0, Constant.draw_type_names.length - 1);
		int bg = clamp(q.get("bg"), 0, 0, Constant.res_type_names.length - 1);
		int scale = scale(q);
		boolean bots = !"0".equals(q.get("bots"));
		String key = mode + "/" + bg + "/" + scale + "/" + bots + "/" + w.population;
		long period = frame_ms;
		synchronized (renderer) {//одновременные запросы одного слоя: первый рисует, остальные берут его кадр
			long now = System.currentTimeMillis();
			long tick = now / period;
			Frame f = frames.get(key);
			if (f == null || f.tick() != tick) {
				if (f != null && f.step() == w.steps) {//мир не сдвинулся — тот же кадр в новом отрезке
					f = new Frame(tick, f.step(), f.time(), f.etag(), f.png());
				}else {
					BufferedImage img;
					int step;
					w.lock.lock();
					try {
						img = renderer.render(w, mode, bg, scale, bots, false);
						step = w.steps;
					}finally {
						w.lock.unlock();
					}
					ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
					ImageIO.write(img, "png", out);//сжатие — уже без блокировки мира
					f = new Frame(tick, step, now, "\"" + key + "/" + step + "\"", out.toByteArray());
					renders++;
				}
				frames.put(key, f);
			}
			return f;
		}
	}
	private static long next_frame_ms(Frame f) {//через сколько будет следующий кадр
		return Math.max(0, (f.tick() + 1) * frame_ms - System.currentTimeMillis());
	}
	private static void frame(HttpExchange ex, Map<String, String> q) throws IOException {
		Frame f = frame_for(q);
		var h = ex.getResponseHeaders();
		h.set("Cache-Control", "no-cache");
		h.set("ETag", f.etag());
		h.set("X-Frame-Step", String.valueOf(f.step()));
		h.set("X-Frame-Time", String.valueOf(f.time()));
		h.set("X-Frame-Period", String.valueOf(frame_ms));
		h.set("X-Next-Frame", String.valueOf(next_frame_ms(f)));
		if (f.etag().equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {//этот кадр у зрителя уже есть
			ex.sendResponseHeaders(304, -1);
			return;
		}
		send(ex, 200, "image/png", f.png());
	}
	//
	//ПОТОК КАДРОВ: один долгий ответ multipart/x-mixed-replace — сервер сам присылает новый кадр слоя в начале
	//каждого отрезка, без опроса. Кадр берется из того же кеша (frame_for), так что рендер — по-прежнему раз за
	//отрезок на слой, а не на зрителя. Мир на паузе — новых частей нет, только раз в keepalive_ms повтор
	//(чтобы прокси не закрыли тихое соединение). Подходит и для <img src="/stream">.
	//Соединений с адреса — не больше streams_per_ip, всего — не больше max_streams.
	//
	static volatile int streams_per_ip = 6;//с запасом: закрытый зрителем поток сервер замечает только при следующей записи (до отрезка)
	static volatile int max_streams = 500;
	private static final long keepalive_ms = 20_000;
	private static final String boundary = "oxygenframe";
	private static final Map<String, Integer> streams = new HashMap<String, Integer>();//адрес -> открытых потоков
	static volatile int streams_open = 0;
	private static void stream(HttpExchange ex, Map<String, String> q) throws IOException {
		String ip = ip(ex);
		synchronized (streams) {
			if (streams_open >= max_streams || streams.getOrDefault(ip, 0) >= streams_per_ip) {
				ex.getResponseHeaders().set("Retry-After", "10");
				json(ex, 429, "{\"ok\":false,\"error\":\"слишком много потоков — смотрите кадрами\"}");
				return;
			}
			streams.merge(ip, 1, Integer::sum);
			streams_open++;
		}
		try {
			var h = ex.getResponseHeaders();
			h.set("Content-Type", "multipart/x-mixed-replace; boundary=" + boundary);
			h.set("Cache-Control", "no-cache, no-store");
			h.set("X-Accel-Buffering", "no");//nginx: не копить поток у себя
			h.set("X-Content-Type-Options", "nosniff");
			ex.sendResponseHeaders(200, 0);//0 — тело частями (chunked), длина заранее неизвестна
			OutputStream out = ex.getResponseBody();
			String last = null;
			long last_sent = 0;
			long population = w.population;
			while (w.population == population) {//новый мир — другой ключ кеша и, может быть, размер: клиент переподключится
				Frame f = frame_for(q);
				long now = System.currentTimeMillis();
				if (!f.etag().equals(last) || now - last_sent >= keepalive_ms) {
					String head = "--" + boundary + "\r\nContent-Type: image/png\r\nContent-Length: " + f.png().length
						+ "\r\nX-Frame-Step: " + f.step() + "\r\nX-Frame-Time: " + f.time() + "\r\nX-Frame-Period: " + frame_ms + "\r\n\r\n";
					out.write(head.getBytes(StandardCharsets.US_ASCII));
					out.write(f.png());
					out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
					out.flush();//зритель ушел — здесь IOException, поток закрывается
					last = f.etag();
					last_sent = now;
				}
				try {
					Thread.sleep(next_frame_ms(f) + 20 + (long) (Math.random() * 80));//в начале следующего отрезка, вразброс
				}catch (InterruptedException e) {
					return;
				}
			}
		}catch (IOException e) {
			//зритель закрыл вкладку или сменил слой — обычное завершение потока
		}finally {
			synchronized (streams) {
				streams.merge(ip, -1, Integer::sum);
				streams.remove(ip, 0);
				streams_open--;
			}
		}
	}
	//
	//МОЗГ СУЩЕСТВА по координатам — единоразовый снимок (BrainInfo). Зрителю — не чаще раза в brain_limit_sec
	//с одного адреса (пустая клетка не считается); порт управления не ограничен.
	//
	static volatile int brain_limit_sec = 10;
	private static final Map<String, Long> brain_last = new HashMap<String, Long>();//адрес -> когда отдан последний мозг
	private static void brain(HttpExchange ex, Map<String, String> q, boolean control) throws IOException {
		int x = Integer.parseInt(q.getOrDefault("x", "0"));
		int y = Integer.parseInt(q.getOrDefault("y", "0"));
		String ip = ip(ex);
		long now = System.currentTimeMillis();
		long limit = brain_limit_sec * 1000L;
		if (!control && limit > 0) {
			synchronized (brain_last) {
				Long last = brain_last.get(ip);
				if (last != null && now - last < limit) {
					long wait = (limit - (now - last) + 999) / 1000;
					ex.getResponseHeaders().set("Retry-After", String.valueOf(wait));
					json(ex, 429, "{\"ok\":false,\"retry_after\":" + wait + ",\"limit_sec\":" + brain_limit_sec
						+ ",\"error\":" + str("мозг можно запрашивать раз в " + brain_limit_sec + " с — подождите " + wait + " с") + "}");
					return;
				}
			}
		}
		String body;
		w.lock.lock();
		try {
			body = BrainInfo.json(w, x, y);
		}finally {
			w.lock.unlock();
		}
		boolean found = !body.contains("\"mode\":\"none\"");
		if (!control && limit > 0 && found) {
			synchronized (brain_last) {
				if (brain_last.size() > 10_000) {//старые адреса — вон
					brain_last.values().removeIf(t -> now - t >= limit);
				}
				brain_last.put(ip, now);
			}
		}
		ex.getResponseHeaders().set("X-Brain-Limit", String.valueOf(control ? 0 : brain_limit_sec));
		json(ex, body);
	}
	private static int clamp(String v, int def, int min, int max) {
		try {
			return v == null ? def : Math.max(min, Math.min(max, Integer.parseInt(v)));
		}catch (NumberFormatException e) {
			return def;
		}
	}
	//
	//СТАТУС И ИСТОРИЯ
	//
	private static String status() {
		StringBuilder sb = new StringBuilder("{");
		w.lock.lock();
		try {
			int last = History.size - 1;
			sb.append("\"steps\":").append(w.steps)
				.append(",\"started\":").append(w.series_started)//запуск серии; попытка после перезапуска — attempt_started
				.append(",\"attempt_started\":").append(w.started)
				.append(",\"restarts\":").append(w.restarts)
				.append(",\"restart_mode\":").append(str(Constant.restart_mode))
				.append(",\"elite\":").append(Constant.elite != 0)
				.append(",\"now\":").append(System.currentTimeMillis())
				.append(",\"world\":").append(w.population)
				.append(",\"width\":").append(Constant.world_scale[0])
				.append(",\"height\":").append(Constant.world_scale[1])
				.append(",\"cell\":").append(Constant.size)
				.append(",\"paused\":").append(w.pause)
				.append(",\"speed\":").append(str(w.speed_name()))
				.append(",\"speed_level\":").append(w.speed_idx)
				.append(",\"ms_per_step\":").append(num(w.ms_per_step))
				.append(",\"steps_per_sec\":").append(num(steps_per_sec))
				.append(",\"renders_per_sec\":").append(num(renders_per_sec))
				.append(",\"renders\":").append(renders)
				.append(",\"frame_ms\":").append(frame_ms)
				.append(",\"brain_limit_sec\":").append(brain_limit_sec)
				.append(",\"view_rps\":").append(num(view_rps))
				.append(",\"view_burst\":").append(view_burst)
				.append(",\"streams\":").append(streams_open)
				.append(",\"bots\":").append(w.b_count)
				.append(",\"seeds\":").append(w.org_count)
				.append(",\"plants\":").append(last < 0 ? 0 : (int) History.data[History.PLANTS][last])
				.append(",\"corpses\":").append(last < 0 ? 0 : (int) History.data[History.CORPSES][last])
				.append(",\"queens\":").append(last < 0 ? 0 : (int) History.data[History.QUEENS][last])
				.append(",\"clans\":").append(KinClans.clans)
				.append(",\"energy\":").append(num(Ledger.total))
				.append(",\"balance_diff\":").append(num(Ledger.total_diff))
				.append(",\"draw_types\":").append(arr(Constant.draw_type_names))
				.append(",\"backgrounds\":").append(arr(Constant.res_type_names))
				.append(",\"populations\":[");
			for (int i = 0; i < w.populations.size(); i++) {
				Population p = w.populations.get(i);
				sb.append(i == 0 ? "" : ",").append("{\"name\":").append(str(p.name))
					.append(",\"species\":").append(str(p.species.name()))
					.append(",\"brain\":").append(str(p.brain))
					.append(",\"start\":").append(p.count)
					.append(",\"alive\":").append(last < 0 || i >= History.POPS ? 0 : (int) History.data[History.POP0 + i][last])
					.append(",\"elite\":").append(w.elite_count(i))
					.append(",\"color\":").append(str(String.format("#%06X", p.color.getRGB() & 0xFFFFFF))).append("}");
			}
		}finally {
			w.lock.unlock();
		}
		return sb.append("]}").toString();
	}
	//группы графиков: имя -> колонки History
	private static Map<String, int[]> groups() {
		Map<String, int[]> g = new java.util.LinkedHashMap<String, int[]>();
		int pops = Math.min(w.populations.size(), History.POPS);
		int[] p = new int[pops];
		for (int i = 0; i < pops; i++) {
			p[i] = History.POP0 + i;
		}
		g.put("populations", p);
		g.put("bots & plants", new int[]{History.BOTS, History.SEEDS, History.PLANTS, History.CORPSES, History.QUEENS});
		g.put("roles", new int[]{History.ROLE0, History.ROLE0 + 1, History.ROLE0 + 2, History.ROLE0 + 3, History.ROLE0 + 4});
		g.put("plant cells", new int[]{History.SPROUTS, History.LEAVES, History.ROOTS, History.TRUNKS, History.CORPSES});
		g.put("gases", new int[]{History.OXYGEN, History.CO2});
		g.put("energy", new int[]{History.ENERGY_CELLS, History.ORGANICS, History.ENERGY_TOTAL, History.SOIL});
		g.put("births & deaths", new int[]{History.BIRTHS, History.DEATHS, History.MUTATIONS});
		g.put("ms/step", new int[]{History.MS});
		return g;
	}
	private static void history(HttpExchange ex, Map<String, String> q) throws IOException {
		int n = clamp(q.get("n"), 400, 10, 5000);
		StringBuilder sb = new StringBuilder("{");
		w.lock.lock();
		try {
			Map<String, int[]> groups = groups();
			sb.append("\"groups\":").append(arr(groups.keySet().toArray(new String[0])));
			int[] cols = groups.getOrDefault(q.getOrDefault("group", ""), groups.values().iterator().next());
			int size = History.size;
			int k = Math.min(n, size);
			int[] idx = new int[k];//равномерно по всей истории, последняя точка — всегда текущая
			for (int i = 0; i < k; i++) {
				idx[i] = k == 1 ? size - 1 : (int) Math.round((double) i * (size - 1) / (k - 1));
			}
			sb.append(",\"steps\":[");
			for (int i = 0; i < k; i++) {
				sb.append(i == 0 ? "" : ",").append(History.step_of[idx[i]]);
			}
			sb.append("],\"series\":[");
			for (int c = 0; c < cols.length; c++) {
				int col = cols[c];
				String color = null;
				if (col >= History.POP0 && col < History.POP0 + History.POPS) {
					color = String.format("#%06X", w.populations.get(col - History.POP0).color.getRGB() & 0xFFFFFF);
				}else if (History.colors[col] != null) {
					color = String.format("#%06X", History.colors[col].getRGB() & 0xFFFFFF);
				}
				sb.append(c == 0 ? "" : ",").append("{\"name\":").append(str(History.names[col]))
					.append(",\"color\":").append(color == null ? "null" : str(color)).append(",\"values\":[");
				for (int i = 0; i < k; i++) {
					sb.append(i == 0 ? "" : ",").append(num(History.data[col][idx[i]]));
				}
				sb.append("]}");
			}
		}finally {
			w.lock.unlock();
		}
		json(ex, sb.append("]}").toString());
	}
	//
	//НАСТРОЙКИ ДЛЯ ВЕБ-РЕДАКТОРА: схема (что можно менять) и настройки в JSON.
	//Обратно редактор шлет .properties (WorldSettings.to_text) — разбор один, как у диалога и файлов.
	//
	private static String schema() {
		StringBuilder sb = new StringBuilder("{");
		sb.append("\"species\":[");
		Species[] all = Species.values();
		for (int i = 0; i < all.length; i++) {
			sb.append(i == 0 ? "" : ",").append("{\"id\":").append(str(all[i].name())).append(",\"title\":").append(str(all[i].title)).append("}");
		}
		sb.append("],\"brains\":{\"OXYGEN_BOT\":").append(arr(Brains.bot_brain_names()))
			.append(",\"PLANT\":").append(arr(Brains.plant_brains)).append("}");
		sb.append(",\"default_brain\":{\"OXYGEN_BOT\":\"genome\",\"PLANT\":\"think_new\"}");
		sb.append(",\"neuro\":").append(str(Brains.NEURO));
		sb.append(",\"actions\":{\"OXYGEN_BOT\":").append(actions(BotAction.values()))
			.append(",\"PLANT\":").append(actions(sct.plants.PlantAction.values())).append("}");
		sb.append(",\"sensors\":{\"OXYGEN_BOT\":").append(arr(sct.neuro.BotBody.instance.inputs()))
			.append(",\"PLANT\":").append(arr(sct.neuro.PlantBody.instance.inputs())).append("}");
		sb.append(",\"hidden_constants\":").append(arr(NewWorldDialog.NewWorldPanel.by_populations.toArray(new String[0])));
		sb.append(",\"world_constants\":").append(arr(NewWorldDialog.NewWorldPanel.world_keys.toArray(new String[0])));//на первой вкладке
		sb.append(",\"elite_fields\":").append(arr(Elite.FIELDS));
		sb.append(",\"constant_types\":{");
		boolean first = true;
		for (java.lang.reflect.Field f : WorldSettings.constant_fields()) {
			sb.append(first ? "" : ",").append(str(f.getName())).append(":").append(str(type(f)));
			first = false;
		}
		sb.append("},\"plant_types\":").append(types(sct.plants.PlantConfig.class));
		sb.append(",\"neuro_types\":").append(types(sct.neuro.NeuroConfig.class));
		sb.append(",\"plant_default\":").append(fields(new sct.plants.PlantConfig()));
		sb.append(",\"neuro_default\":").append(fields(sct.neuro.NeuroConfig.current));
		sb.append(",\"plant_presets\":{");
		first = true;
		for (String name : sct.plants.PlantConfig.preset_names()) {
			sb.append(first ? "" : ",").append(str(name)).append(":").append(fields(sct.plants.PlantConfig.preset(name)));
			first = false;
		}
		return sb.append("}}").toString();
	}
	private static String actions(Action<?>[] all) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < all.length; i++) {
			sb.append(i == 0 ? "" : ",").append("{\"id\":").append(str(all[i].id())).append(",\"category\":").append(str(all[i].category())).append("}");
		}
		return sb.append("]").toString();
	}
	private static String type(java.lang.reflect.Field f) {
		Class<?> t = f.getType();
		return t == int.class ? "int" : t == double.class ? "double" : t == boolean.class ? "boolean" : t == int[].class ? "int[]" : "string";
	}
	private static String types(Class<?> c) {//поля экземпляра: имя -> тип
		StringBuilder sb = new StringBuilder("{");
		for (java.lang.reflect.Field f : c.getFields()) {
			if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
				sb.append(sb.length() > 1 ? "," : "").append(str(f.getName())).append(":").append(str(type(f)));
			}
		}
		return sb.append("}").toString();
	}
	private static String fields(Object o) {//поля экземпляра: имя -> значение строкой
		StringBuilder sb = new StringBuilder("{");
		for (java.lang.reflect.Field f : o.getClass().getFields()) {
			if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
				sb.append(sb.length() > 1 ? "," : "").append(str(f.getName())).append(":").append(str(WorldSettings.field_value(f, o)));
			}
		}
		return sb.append("}").toString();
	}
	static String settings_json(WorldSettings s) {
		StringBuilder sb = new StringBuilder("{\"seed\":").append(s.seed).append(",\"constants\":{");
		boolean first = true;
		for (Map.Entry<String, String> e : s.constants.entrySet()) {
			sb.append(first ? "" : ",").append(str(e.getKey())).append(":").append(str(e.getValue()));
			first = false;
		}
		sb.append("},\"populations\":[");
		for (int i = 0; i < s.populations.size(); i++) {
			Population p = s.populations.get(i);
			sb.append(i == 0 ? "" : ",").append("{\"name\":").append(str(p.name))
				.append(",\"species\":").append(str(p.species.name()))
				.append(",\"brain\":").append(str(p.brain))
				.append(",\"count\":").append(p.count)
				.append(",\"color\":").append(str(String.format("#%06X", p.color.getRGB() & 0xFFFFFF)))
				.append(",\"actions_on\":[");
			for (int a = 0; a < p.actions_on.length; a++) {
				sb.append(a == 0 ? "" : ",").append(p.actions_on[a]);
			}
			sb.append("],\"plant\":").append(p.plant_config == null ? "null" : fields(p.plant_config))
				.append(",\"neuro\":").append(p.neuro_config == null ? "null" : fields(p.neuro_config)).append("}");
		}
		int n = s.populations.size();
		Interaction ia = WorldSettings.copy(s.interaction, n);
		sb.append("],\"interaction\":{");
		String[] names = {"eat", "exchange", "share", "chain", "seed_kills"};
		for (int k = 0; k < names.length; k++) {
			sb.append(k == 0 ? "" : ",").append(str(names[k])).append(":[");
			for (int a = 0; a < n; a++) {
				sb.append(a == 0 ? "[" : ",[");
				for (int b = 0; b < n; b++) {
					sb.append(b == 0 ? "" : ",");
					switch (k) {
						case 0: sb.append(ia.eat[a][b]); break;
						case 1: sb.append(ia.exchange[a][b]); break;
						case 2: sb.append(ia.share[a][b]); break;
						case 3: sb.append(ia.chain[a][b]); break;
						default: sb.append(ia.seed_kills[a][b]);
					}
				}
				sb.append("]");
			}
			sb.append("]");
		}
		return sb.append("}}").toString();
	}
	//
	//HTTP И JSON
	//
	private static Map<String, String> query(HttpExchange ex) {
		Map<String, String> q = new HashMap<String, String>();
		String raw = ex.getRequestURI().getRawQuery();
		if (raw != null) {
			for (String pair : raw.split("&")) {
				int eq = pair.indexOf('=');
				String k = eq < 0 ? pair : pair.substring(0, eq);
				String v = eq < 0 ? "" : pair.substring(eq + 1);
				q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
			}
		}
		return q;
	}
	private static String body(HttpExchange ex) throws IOException {
		try (InputStream in = ex.getRequestBody()) {
			byte[] b = in.readNBytes(4 * 1024 * 1024);//настройки — килобайты; больше — не настройки
			return new String(b, StandardCharsets.UTF_8);
		}
	}
	private static void send_resource(HttpExchange ex, String name) throws IOException {//страницы — в sct/web на classpath
		try (InputStream in = Server.class.getResourceAsStream("web/" + name)) {
			if (in == null) {
				send(ex, 500, "text/plain; charset=utf-8", "нет ресурса sct/web/" + name + " — скопируйте src/sct/web в каталог классов");
				return;
			}
			send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
		}
	}
	private static void ok(HttpExchange ex) throws IOException {
		json(ex, "{\"ok\":true}");
	}
	private static void json(HttpExchange ex, String body) throws IOException {
		json(ex, 200, body);
	}
	private static void json(HttpExchange ex, int code, String body) throws IOException {
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		send(ex, code, "application/json; charset=utf-8", body);
	}
	private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
		send(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
	}
	private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
		ex.getResponseHeaders().set("Content-Type", type);
		ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(body);
		}
	}
	static String str(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20 || c == '<') {//< — чтобы JSON нельзя было выдать за разметку
						sb.append(String.format("\\u%04x", (int) c));
					}else {
						sb.append(c);
					}
			}
		}
		return sb.append('"').toString();
	}
	private static String num(double v) {
		return Double.isFinite(v) ? String.valueOf((float) v) : "null";
	}
	private static String arr(String[] a) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < a.length; i++) {
			sb.append(i == 0 ? "" : ",").append(str(a[i]));
		}
		return sb.append("]").toString();
	}
}
