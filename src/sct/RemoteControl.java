package sct;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import javax.swing.*;

//Пульт сервера (sct.Server) с рабочего компьютера: пауза, скорость, сохранение — и тот же диалог «Новый мир»,
//что в окне программы, только «Создать» отправляет настройки на сервер.
//  java -cp out sct.RemoteControl [http://сервер:8081] [токен]
//Токен можно задать переменной OXYGEN_CONTROL_TOKEN.
public class RemoteControl {
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final JTextField field_url = new JTextField(24);
	private final JPasswordField field_token = new JPasswordField(12);
	private final JLabel label_status = new JLabel(" ");
	private final JButton button_pause = new JButton("Pause");
	private JFrame frame;
	private volatile boolean paused;

	public static void main(String[] args) {
		Theme.install_look_and_feel();
		RemoteControl rc = new RemoteControl();
		rc.field_url.setText(args.length > 0 ? args[0] : "http://localhost:8081");
		String token = args.length > 1 ? args[1] : System.getenv("OXYGEN_CONTROL_TOKEN");
		rc.field_token.setText(token == null ? "" : token);
		SwingUtilities.invokeLater(rc::show);
	}
	//
	private void show() {
		frame = new JFrame("Oxygen — remote control");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("server"));
		top.add(field_url);
		top.add(new JLabel("token"));
		top.add(field_token);
		JPanel buttons = new JPanel(new GridLayout(0, 4, 4, 4));
		button_pause.addActionListener(e -> post(paused ? "/api/resume" : "/api/pause"));
		buttons.add(button_pause);
		buttons.add(button("Step", () -> post("/api/step")));
		buttons.add(button("slower", () -> post("/api/speed?delta=-1")));
		buttons.add(button("faster", () -> post("/api/speed?delta=1")));
		buttons.add(button("Again", () -> {
			if (confirm("Пересоздать мир с теми же настройками?")) {
				post("/api/again");
			}
		}));
		buttons.add(button("New world...", this::new_world));
		buttons.add(button("Save world", this::save_world));
		buttons.add(button("Open in browser", () -> {
			try {
				Desktop.getDesktop().browse(URI.create(base()));
			}catch (IOException | UnsupportedOperationException ex) {
				error(ex);
			}
		}));
		JPanel content = new JPanel(new BorderLayout(6, 6));
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		content.add(top, BorderLayout.NORTH);
		content.add(buttons, BorderLayout.CENTER);
		content.add(label_status, BorderLayout.SOUTH);
		frame.setContentPane(content);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		new Timer(1000, e -> refresh()).start();
		refresh();
	}
	private JButton button(String text, Runnable r) {
		JButton b = new JButton(text);
		b.addActionListener(e -> r.run());
		return b;
	}
	private boolean confirm(String text) {
		return JOptionPane.showConfirmDialog(frame, text, "Oxygen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}
	private void error(Exception e) {
		JOptionPane.showMessageDialog(frame, e.getMessage(), "Oxygen", JOptionPane.ERROR_MESSAGE);
	}
	//
	//СЕРВЕР
	//
	private String base() {
		String u = field_url.getText().trim();
		return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
	}
	private HttpRequest.Builder request(String path) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(60));
		String token = new String(field_token.getPassword());
		if (!token.isEmpty()) {
			b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes(StandardCharsets.UTF_8)));
		}
		return b;
	}
	private String call(HttpRequest r) throws IOException {
		try {
			HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() == 401) {
				throw new IOException("сервер не принял токен");
			}
			if (resp.statusCode() >= 300) {
				throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
			}
			return resp.body();
		}catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}catch (IllegalArgumentException e) {
			throw new IOException("неверный адрес сервера: " + e.getMessage());
		}
	}
	private String get(String path) throws IOException {
		return call(request(path).GET().build());
	}
	private String post_sync(String path, String body) throws IOException {
		return call(request(path).header("X-Oxygen", "1").header("Content-Type", "text/plain; charset=utf-8")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
	}
	private void post(String path) {//в фоне: окно не ждет сервер
		new Thread(() -> {
			try {
				post_sync(path, "");
				SwingUtilities.invokeLater(this::refresh);
			}catch (IOException e) {
				SwingUtilities.invokeLater(() -> error(e));
			}
		}, "remote").start();
	}
	private volatile boolean refreshing;
	private void refresh() {
		if (refreshing) {
			return;
		}
		refreshing = true;
		new Thread(() -> {
			String text;
			boolean p = paused;
			try {
				String s = get("/api/status");
				p = s.contains("\"paused\":true");
				text = "step " + field(s, "steps") + "   bots " + field(s, "bots") + "   plants " + field(s, "plants")
					+ "   " + Math.round(Double.parseDouble(field(s, "steps_per_sec"))) + " steps/s   " + (p ? "paused" : field(s, "speed").replace("\"", ""));
			}catch (IOException | RuntimeException e) {
				text = "нет связи: " + e.getMessage();
			}
			String t = text;
			boolean fp = p;
			SwingUtilities.invokeLater(() -> {
				paused = fp;
				label_status.setText(t);
				button_pause.setText(fp ? "Resume" : "Pause");
				refreshing = false;
			});
		}, "remote-status").start();
	}
	private static String field(String json, String name) {//значение поля верхнего уровня из ответа /api/status
		int i = json.indexOf("\"" + name + "\":");
		if (i < 0) {
			return "?";
		}
		int start = i + name.length() + 3;
		int end = start;
		if (json.charAt(start) == '"') {
			end = json.indexOf('"', start + 1) + 1;
		}else {
			while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
				end++;
			}
		}
		return json.substring(start, end);
	}
	//
	private void new_world() {//настройки — с сервера; проверку и подтверждение уже сделал диалог, поэтому force=1
		WorldSettings s;
		try {
			s = WorldSettings.from_text(get("/api/settings"));
		}catch (IOException e) {
			error(e);
			return;
		}
		NewWorldDialog.open(frame, s, settings -> {
			try {
				post_sync("/api/world/new?force=1", settings.to_text());
			}catch (IOException e) {
				throw new UncheckedIOException(e.getMessage(), e);//диалог покажет сообщение и останется открытым
			}
		});
		refresh();
	}
	private void save_world() {
		String name = JOptionPane.showInputDialog(frame, "Имя файла на сервере (saved worlds):", "world.oxw");
		if (name == null || name.isBlank()) {
			return;
		}
		name = name.trim().endsWith(".oxw") ? name.trim() : name.trim() + ".oxw";
		post("/api/save?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
	}
}
