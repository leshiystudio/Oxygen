package sct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

//Прогон без окна: скорость, метрики, CSV.
//  java -cp out sct.Bench --steps 1000 --seed 1 --csv metrics.csv [--preset ... --set ...]
//Остальные аргументы — как у Main (см. Presets).
public class Bench {
	public static void main(String[] args) throws IOException {
		int steps = 1000;
		String csv = null;
		List<String> rest = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--steps")) {
				steps = Integer.parseInt(args[++i]);
			}else if (args[i].equals("--csv")) {
				csv = args[++i];
			}else {
				rest.add(args[i]);
			}
		}
		Presets.apply_args(rest.toArray(new String[0]));
		WorldBio w = new WorldBio(false);
		long start = System.nanoTime();
		long block = start;
		for (int i = 1; i <= steps; i++) {
			long t0 = System.nanoTime();
			w.step();
			w.ms_per_step = (System.nanoTime() - t0) / 1e6;
			if (i % 100 == 0 || i == steps) {
				double ms = (System.nanoTime() - block) / 1e6 / (i % 100 == 0 ? 100 : i % 100);
				block = System.nanoTime();
				int last = History.size - 1;
				System.out.printf("step %6d  bots %6d  seeds %5d  queens %3d  plants %6d  corpses %5d  clans %5d  energy %.4gM  births %4d  deaths %4d  balance %.2g  %.2f ms/step%n",
					i, w.b_count, w.org_count, (int) History.data[History.QUEENS][last], (int) History.data[History.PLANTS][last],
					(int) History.data[History.CORPSES][last], KinClans.clans,
					Ledger.total / 1e6, Ledger.births, Ledger.deaths_total(), Ledger.total_diff, ms);
			}
		}
		System.out.printf("total: %d steps, %.2f ms/step%n", steps, (System.nanoTime() - start) / 1e6 / steps);
		if (csv != null) {
			Files.writeString(Paths.get(csv), History.csv(), StandardCharsets.UTF_8);
			System.out.println("csv: " + csv);
		}
		System.exit(0);
	}
}
