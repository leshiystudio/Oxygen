package sct;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

//ВИДЕО «ПОСЛЕДНИЙ ЧАС» одного вида (режим ботов + фон) — план в docs/video-plan.md.
//Поток захвата раз в record_ms под w.lock рисует вид в 1 px и копирует пиксели (4–7 мс на мире 400x400 —
//это и есть вся цена для симуляции), поток записи отдает сырые пиксели в ffmpeg через stdin.
//По шагам (--record-steps) кадр снимает поток симуляции в конце каждого record_steps-го шага.
//ffmpeg пишет HLS со скользящим окном: сегмент — минута реального времени (по шагам — 60 кадров), в плейлисте minutes сегментов,
//старые он удаляет сам. Пересобирать ничего не нужно.
//Между потоками — очередь на 2 кадра: ffmpeg не успевает — старый кадр выбрасывается (dropped), захват не ждет.
//Мир на паузе — кадры не пишутся: окно — последний час работы мира. Новый мир того же размера — тот же поток
//ffmpeg, запись без шва; другой размер — ffmpeg перезапускается, в плейлисте разрыв (#EXT-X-DISCONTINUITY).
//ffmpeg упал или его нет — повтор с паузой, симуляция об этом не знает.
public class VideoRecorder {
	public static int record_ms = 1000;//кадр раз в столько мс реального времени
	public static int minutes = 60;//длина окна
	//по шагам вместо времени: кадр ровно на каждом record_steps-м шаге попытки (0 — по времени, record_ms).
	//Снимается в потоке симуляции в конце шага (WorldBio.after_step) — цена та же, рендер 4–7 мс, но платится
	//за каждый кадр: при шаге 10 мс и record_steps 50 — ~1 %, при 10 — ~5 %. Окно — window_steps шагов,
	//сегмент — 60 кадров (record_steps * 60 шагов)
	public static int record_steps = 0;
	public static int window_steps = 0;//0 — 3600 кадров, как час при кадре в секунду
	static final int frames_per_step_segment = 60;
	public static int fps = 30;//скорость проигрывания: кадров записи в секунде видео
	//качество H.264: больше — меньше файл. Кадры мира раз в секунду почти не похожи друг на друга, поэтому сжимаются плохо:
	//на мире 400x400 (кадр 800x800) замер — crf 20 ~4 МБ, crf 28 ~2,3 МБ на минуту записи
	public static int crf = 28;
	public static File root = new File("video");//каталог видов: <root>/<вид>/index.m3u8
	public static String ffmpeg = "ffmpeg";
	public static final List<VideoRecorder> all = new ArrayList<VideoRecorder>();
	//
	public final int mode, bg;
	public final String name;//"режим_фон" — имя каталога и адреса /video/<name>/
	public final File dir;
	final WorldBio w;
	private final PixelRenderer renderer = new PixelRenderer();//свой: запись и зрители не ждут друг друга
	private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<Frame>(2);
	private int last_step = -1, last_population = -1;
	public volatile int frames = 0;//отдано кодировщику
	public volatile int dropped = 0;//выброшено: кодировщик не успевал
	public volatile String state = "starting";//running, restarting, missing (ffmpeg не найден), stopped
	//
	record Frame(int width, int height, int[] px) {}
	//
	//Кодировщик: start — поток для сырых кадров bgr0 размера width x height, stop — закончить запись.
	//По умолчанию — процесс ffmpeg; тесты подставляют свой.
	interface Encoder {
		OutputStream start(int width, int height) throws IOException;
		void stop();
	}
	Encoder encoder = new Ffmpeg();
	private OutputStream out;
	private int out_w, out_h;
	//
	public VideoRecorder(WorldBio w, int mode, int bg) {
		this.w = w;
		this.mode = mode;
		this.bg = bg;
		this.name = mode + "_" + bg;
		this.dir = new File(root, name);
	}
	//
	public static List<int[]> parse_views(String list) {//"0_2,8_0" -> {режим, фон}; ошибка — с подсказкой
		List<int[]> views = new ArrayList<int[]>();
		for (String v : list.split(",")) {
			v = v.trim();
			if (v.isEmpty()) {
				continue;
			}
			if (!v.matches("\\d{1,2}_\\d{1,2}")) {
				throw new IllegalArgumentException("--record: вид «" + v + "» — нужно режим_фон, например 0_2");
			}
			int m = Integer.parseInt(v.substring(0, v.indexOf('_')));
			int b = Integer.parseInt(v.substring(v.indexOf('_') + 1));
			if (m >= Constant.draw_type_names.length || b >= Constant.res_type_names.length) {
				throw new IllegalArgumentException("--record: вид «" + v + "» — режим 0.." + (Constant.draw_type_names.length - 1)
					+ ", фон 0.." + (Constant.res_type_names.length - 1));
			}
			for (int[] e : views) {
				if (e[0] == m && e[1] == b) {
					throw new IllegalArgumentException("--record: вид «" + v + "» указан дважды");
				}
			}
			views.add(new int[]{m, b});
		}
		return views;
	}
	public static boolean valid_file(String f) {//что можно отдать из каталога вида
		return f.equals("index.m3u8") || f.matches("seg_\\d{5,9}\\.ts");
	}
	public static VideoRecorder find(String name) {
		for (VideoRecorder r : all) {
			if (r.name.equals(name)) {
				return r;
			}
		}
		return null;
	}
	//
	public void start() {
		dir.mkdirs();
		if (record_steps <= 0) {//по шагам захват вызывает поток симуляции (on_step), своего потока нет
			Thread c = new Thread(this::capture_loop, "video-capture-" + name);
			c.setDaemon(true);
			c.start();
		}
		Thread wr = new Thread(this::write_loop, "video-write-" + name);
		wr.setDaemon(true);
		wr.start();
	}
	private void capture_loop() {
		long next = System.currentTimeMillis();
		while (true) {
			next += record_ms;
			long wait = next - System.currentTimeMillis();
			if (wait < 0) {//отстали (долгая блокировка, сон машины) — не наверстывать пачкой
				next = System.currentTimeMillis();
			}else {
				try {
					Thread.sleep(wait);
				}catch (InterruptedException e) {
					return;
				}
			}
			capture();
		}
	}
	public void on_step() {//конец шага, под w.lock: кадр на каждом record_steps-м шаге
		if (record_steps > 0 && w.steps > 0 && w.steps % record_steps == 0) {
			capture();
		}
	}
	static int segment_frames() {//кадров в сегменте: минута реального времени или 60 кадров по шагам
		return record_steps > 0 ? frames_per_step_segment : Math.max(1, 60_000 / record_ms);
	}
	static int segments() {//сегментов в окне
		if (record_steps <= 0) {
			return minutes;
		}
		long steps = window_steps > 0 ? window_steps : 3600L * record_steps;
		return (int) Math.max(1, (steps + (long) record_steps * frames_per_step_segment - 1) / ((long) record_steps * frames_per_step_segment));
	}
	//
	boolean capture() {//снять кадр, если мир сдвинулся; false — пауза
		int[] px;
		int width, height;
		w.lock.lock();
		try {
			if (w.steps == last_step && w.population == last_population) {
				return false;
			}
			last_step = w.steps;
			last_population = w.population;
			BufferedImage img = renderer.render(w, mode, bg, 1, true, false);
			width = img.getWidth();
			height = img.getHeight();
			px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData().clone();
		}finally {
			w.lock.unlock();
		}
		offer(new Frame(width, height, px));
		return true;
	}
	int queued() {
		return queue.size();
	}
	void offer(Frame f) {
		while (!queue.offer(f)) {//полно — самый старый вон
			if (queue.poll() != null) {
				dropped++;
			}
		}
	}
	//
	private void write_loop() {
		long backoff = 5_000;
		while (true) {
			try {
				write_next(Long.MAX_VALUE);
				backoff = 5_000;
			}catch (InterruptedException e) {
				close();
				return;
			}catch (IOException e) {
				boolean missing = out == null;//не запустился вовсе
				close();
				String s = missing ? "missing" : "restarting";
				if (!s.equals(state)) {
					Server.log("видео " + name + ": " + (missing ? "ffmpeg не запускается (" + e.getMessage() + ") — повтор через " + backoff / 1000 + " с"
						: "ffmpeg прервался (" + e.getMessage() + ") — перезапуск"));
				}
				state = s;
				queue.clear();
				try {
					Thread.sleep(backoff);
				}catch (InterruptedException ie) {
					return;
				}
				backoff = Math.min(60_000, backoff * 2);
			}
		}
	}
	boolean write_next(long timeout_ms) throws IOException, InterruptedException {//записать следующий кадр; false — не дождались
		Frame f = queue.poll(timeout_ms, TimeUnit.MILLISECONDS);
		if (f == null) {
			return false;
		}
		if (out != null && (f.width() != out_w || f.height() != out_h)) {//новый размер мира — новый поток кодировщика
			close();
		}
		if (out == null) {
			out = encoder.start(f.width(), f.height());
			out_w = f.width();
			out_h = f.height();
			if (!"running".equals(state)) {
				Server.log("видео " + name + ": запись " + out_w + "x" + out_h + " в " + dir.getPath());
			}
			state = "running";
		}
		ByteBuffer b = ByteBuffer.allocate(f.px().length * 4).order(ByteOrder.LITTLE_ENDIAN);//0x00RRGGBB -> байты B G R 0 = bgr0
		b.asIntBuffer().put(f.px());
		out.write(b.array());
		out.flush();
		frames++;
		return true;
	}
	void close() {
		if (out != null) {
			try {
				out.close();
			}catch (IOException e) {
				//кодировщик уже закрыт
			}
			out = null;
		}
		encoder.stop();
	}
	public void stop() {//при остановке сервера: ffmpeg дописывает последний сегмент
		state = "stopped";
		close();
	}
	//
	public String json() {
		return "{\"view\":" + Server.str(name) + ",\"mode\":" + mode + ",\"bg\":" + bg
			+ ",\"mode_name\":" + Server.str(Constant.draw_type_names[mode]) + ",\"bg_name\":" + Server.str(Constant.res_type_names[bg])
			+ ",\"url\":" + Server.str("/video/" + name + "/index.m3u8")
			+ ",\"frames\":" + frames + ",\"dropped\":" + dropped + ",\"state\":" + Server.str(state) + "}";
	}
	public static String settings_json() {
		return "\"record_ms\":" + record_ms + ",\"minutes\":" + minutes + ",\"fps\":" + fps
			+ ",\"record_steps\":" + record_steps + ",\"window_steps\":" + (record_steps > 0 ? (long) segments() * record_steps * frames_per_step_segment : 0);
	}
	//
	//ffmpeg: сырые кадры -> H.264 -> HLS. Кадр удваивается без сглаживания: в H.264 для браузера цвет хранится
	//с половинным разрешением (4:2:0), и бот в 1 пиксель расплылся бы. Ключевой кадр — в начале каждого сегмента.
	//append_list: перезапуск (ffmpeg упал, контейнер перезапущен, сменился размер) продолжает тот же плейлист с разрывом.
	//preset medium: при кадре в секунду процессора на него все равно ~2 % ядра, а файл меньше, чем у veryfast.
	private class Ffmpeg implements Encoder {
		private Process p;
		public OutputStream start(int width, int height) throws IOException {
			int per_segment = segment_frames();
			File playlist = new File(dir, "index.m3u8");
			String flags = "delete_segments+independent_segments+program_date_time"
				+ (playlist.isFile() ? "+append_list+discont_start" : "");//продолжить окно прошлого запуска, с разрывом
			List<String> cmd = new ArrayList<String>();
			if (new File("/usr/bin/nice").canExecute()) {
				cmd.add("/usr/bin/nice");
				cmd.add("-n");
				cmd.add("19");
			}
			cmd.addAll(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
				"-f", "rawvideo", "-pix_fmt", "bgr0", "-s", width + "x" + height, "-framerate", String.valueOf(fps), "-i", "-",
				"-vf", "scale=iw*2:ih*2:flags=neighbor,format=yuv420p",
				"-c:v", "libx264", "-preset", "medium", "-tune", "animation", "-crf", String.valueOf(crf), "-threads", "1",
				"-g", String.valueOf(per_segment), "-keyint_min", String.valueOf(per_segment), "-sc_threshold", "0",
				"-f", "hls", "-hls_time", String.valueOf((double) per_segment / fps), "-hls_list_size", String.valueOf(segments()),
				"-hls_flags", flags, "-hls_segment_filename", new File(dir, "seg_%05d.ts").getPath(), playlist.getPath()));
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);//ошибки ffmpeg — в лог сервера (docker compose logs)
			p = pb.start();
			return p.getOutputStream();
		}
		public void stop() {
			if (p == null) {
				return;
			}
			try {
				if (!p.waitFor(10, TimeUnit.SECONDS)) {//stdin закрыт — ffmpeg дописывает сегмент и выходит сам
					p.destroyForcibly();
				}
			}catch (InterruptedException e) {
				p.destroyForcibly();
				Thread.currentThread().interrupt();
			}
			p = null;
		}
	}
}
