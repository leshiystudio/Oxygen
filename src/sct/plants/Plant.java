package sct.plants;

import java.util.ListIterator;

import sct.BotSuper;
import sct.Brain;
import sct.IBot;
import sct.Ledger;
import sct.Species;

//Клетка растения — перенос модели biosim (engine.py). Одна клетка дерева или росток.
//
//Поля — строка S из biosim, функции — с теми же именами (kill, take_energy, birth, reproduce,
//eat_dir, body_step, lifecycle, corpse_step, mineralize), чтобы их можно было сверять построчно.
//Энергия целочисленная, как в biosim. Почва — слой минералов мира (PlantWorld.soil), органика — общая.
//
//Отличия от biosim только в хранении: клетка — объект в общей сетке мира, а не строка массива;
//список обхода — общий список мира (потомок встает перед родителем, как link_before);
//соседи другого вида (не растения) — занятая клетка, всегда «чужая».
public class Plant extends BotSuper {
	//типы клеток (как в biosim) и OTHER — существо другого вида
	public static final int EMPTY = 0, ILIVE = 1, IDEAD = 2, ROOT = 3, LEAF = 4, TRANS = 5, OTHER = 6;
	public static final String[] type_names = {"EMPTY", "ILIVE", "IDEAD", "ROOT", "LEAF", "TRANS", "OTHER"};
	//направления biosim: 0..3 — LEFT, UP, RIGHT, DOWN; 4..7 — диагонали (с таблицей ботов не смешивать)
	static final int[] DX = {-1, 0, 1, 0, -1, 1, 1, -1};
	static final int[] DY = {0, -1, 0, 1, -1, -1, 1, 1};
	//
	public final PlantWorld w;
	public Brain<Plant> brain;
	public int type;
	public int energy;
	public int health;
	public int age;
	public int cmd;//указатель команды (THINK_OLD)
	public int gen;//активный ген (THINK_NEW)
	public int head;//направление «головы» 0..3
	public int dpar = -1;//направление на родителя, -1 — нет
	public int dmask;//маска направлений, куда тело отправляет энергию
	public int ina;//энергия в пути, пришла в этом тике
	public int inb;//энергия в пути, пришла тиком раньше
	public int color;//цвет клана: наследуется, слегка меняется при мутации
	public int ghash;//хеш генома
	public byte[] genome;//значения 0..max_number_cmd-1 (читать через & 0xFF)

	public Plant(PlantWorld w, int x, int y, Brain<Plant> brain) {
		this.w = w;
		this.xpos = x;
		this.ypos = y;
		this.brain = brain;
		this.pop = w.pop;
		this.genome = new byte[w.c.genome_size()];
	}
	public static Plant seedling(PlantWorld w, int x, int y, Brain<Plant> brain) {//росток при создании мира (init_world)
		PlantConfig c = w.c;
		Plant p = new Plant(w, x, y, brain);
		for (int k = 0; k < p.genome.length; k++) {
			p.genome[k] = (byte) PlantWorld.rand.nextInt(0, c.max_number_cmd);
		}
		p.type = ILIVE;
		p.energy = c.energy_start_value;
		p.health = c.health_start_value;
		p.head = PlantWorld.rand.nextInt(0, 4);
		p.dpar = -1;
		p.color = random_color();
		p.ghash = p.genome_hash();
		return p;
	}
	//
	//СОСЕДИ
	//
	int nx(int d) {
		return Math.floorMod(xpos + DX[d], w.W);
	}
	int ny(int d) {
		return Math.floorMod(ypos + DY[d], w.H);
	}
	IBot at(int d) {
		return w.grid[nx(d)][ny(d)];
	}
	public int type_at(int d) {//тип соседа: EMPTY, тип растения или OTHER
		IBot o = at(d);
		if (o == null) {
			return EMPTY;
		}
		return o instanceof Plant ? ((Plant) o).type : OTHER;
	}
	static boolean is_alive_type(int t) {
		return t == ILIVE || t == ROOT || t == LEAF || t == TRANS;
	}
	static boolean is_body_type(int t) {
		return t == ROOT || t == LEAF || t == TRANS;
	}
	//
	//РОДСТВО
	//
	public boolean is_enemy(IBot o) {//is_enemy_s: другой вид и другая популяция — всегда чужие
		if (!(o instanceof Plant) || !same_population(o)) {
			return true;
		}
		Plant p = (Plant) o;
		if (brain instanceof sct.neuro.NeuroBrain<?> nb && nb.c.kin_by_weights && p.brain instanceof sct.neuro.NeuroBrain<?> other) {
			return !nb.same_kin(other);//родство NeuroPlant — по весам сети
		}
		if (p.ghash == ghash) {
			return false;
		}
		int cnt = 0;
		int max = w.c.max_mind_diff_count;
		for (int k = 0; k < genome.length; k++) {
			if (genome[k] != p.genome[k] && ++cnt > max) {
				return true;
			}
		}
		return false;
	}
	int genome_hash() {//31-битный FNV-хеш генома
		long h = 0xcbf29ce484222325L;
		for (byte b : genome) {
			h = (h ^ (b & 0xFF)) * 0x100000001b3L;
		}
		h ^= h >>> 33;
		return (int)(h & 0x7FFFFFFF);
	}
	//
	//БАЗОВЫЕ ОПЕРАЦИИ
	//
	void remove() {//remove_cell: убрать клетку из мира без остатка (энергию нужно забрать заранее)
		if (killed == 0 && w.grid[xpos][ypos] == this) {
			w.grid[xpos][ypos] = null;
		}
		killed = 1;
		energy = 0;
		ina = 0;
		inb = 0;
	}
	void dump_around(int x, int y, long v) {//раздать энергию в почву 3x3 поровну, остаток — в центр
		if (v <= 0) {
			return;
		}
		long per = v / 9;
		long rem = v - per * 9;
		w.soil[x][y] += per + rem;
		if (per > 0) {
			for (int d = 0; d < 8; d++) {
				w.soil[Math.floorMod(x + DX[d], w.W)][Math.floorMod(y + DY[d], w.H)] += per;
			}
		}
	}
	void add_energy(long v) {//излишек сверх max_energy уходит в почву под клеткой
		long e = energy + v;
		if (e > w.c.max_energy) {
			w.soil[xpos][ypos] += e - w.c.max_energy;
			e = w.c.max_energy;
		}
		energy = (int) e;
	}
	void add_health(int v) {
		int h = health + v;
		if (w.c.max_health > 0 && h > w.c.max_health) {
			h = w.c.max_health;
		}
		health = h;
	}
	public void kill(int cause) {//смерть живой клетки; повторный вызов ничего не делает
		if (killed != 0 || !is_alive_type(type)) {
			return;
		}
		Ledger.die(cause);
		long total = (long) energy + ina + inb;
		if (w.c.enable_corpses) {
			int keep = (int)(total * w.c.corpse_share);
			dump_around(xpos, ypos, total - keep);
			type = IDEAD;
			energy = keep;
			ina = 0;
			inb = 0;
			dmask = 0;
			dpar = -1;
			health = 0;
		}else {
			dump_around(xpos, ypos, total);
			energy = 0;
			ina = 0;
			inb = 0;
			remove();
		}
	}
	static final int STEP = 0, PUNISH = 1;
	boolean take_energy(int v, int kind) {//расход; не хватило — тратится здоровье. false — клетка умерла
		int e = energy;
		int taken = v < e ? v : e;
		if (taken > 0) {
			if (kind == STEP) {
				Ledger.out_plant_step += taken;
			}else {
				Ledger.out_plant_punish += taken;
			}
		}
		e -= taken;
		energy = e;
		if (e <= 0) {
			health -= w.c.health_for_energy;
			if (health < 1) {
				kill(Ledger.DIE_HEALTH);
				return false;
			}
			energy = w.c.energy_from_health;
			Ledger.in_plant_health += w.c.energy_from_health;
		}
		return true;
	}
	static void send_energy_to(Plant j, int v, PlantConfig c) {
		if (v <= 0) {
			return;
		}
		if (c.energy_transfer_delay <= 0) {
			j.add_energy(v);
		}else {
			j.ina += v;
		}
	}
	static int color_drift(int col) {
		int r = (col >> 16) & 255;
		int g = (col >> 8) & 255;
		int b = col & 255;
		r = Math.min(255, Math.max(40, r + PlantWorld.rand.nextInt(-12, 13)));
		g = Math.min(255, Math.max(40, g + PlantWorld.rand.nextInt(-12, 13)));
		b = Math.min(255, Math.max(40, b + PlantWorld.rand.nextInt(-12, 13)));
		return (r << 16) | (g << 8) | b;
	}
	static int random_color() {
		return (PlantWorld.rand.nextInt(40, 256) << 16) | (PlantWorld.rand.nextInt(40, 256) << 8) | PlantWorld.rand.nextInt(40, 256);
	}
	void mutate(int n) {
		PlantConfig c = w.c;
		int gs = c.genome_size();
		for (int i = 0; i < n; i++) {
			int k = PlantWorld.rand.nextInt(0, gs);
			if (c.mutation_mode == PlantConfig.MUT_DRIFT) {
				int v = (genome[k] & 0xFF) + PlantWorld.rand.nextInt(-c.drift_max, c.drift_max + 1);
				if (v < 0) {
					v = 0;
				}
				if (v > c.max_number_cmd - 1) {
					v = c.max_number_cmd - 1;
				}
				genome[k] = (byte) v;
			}else {
				genome[k] = (byte) PlantWorld.rand.nextInt(0, c.max_number_cmd);
			}
		}
		Ledger.mutations += n;
		color = color_drift(color);
		ghash = genome_hash();
	}
	//
	//РОЖДЕНИЕ
	//
	Plant birth(int d, int t, ListIterator<IBot> it) {//потомок типа t в направлении d (0..3) или null
		if (at(d) != null) {
			return null;
		}
		PlantConfig c = w.c;
		int e = energy;
		if (e < c.cost_birth + c.birth_min_energy) {
			return null;
		}
		e -= c.cost_birth;
		Ledger.out_plant_birth += c.cost_birth;
		int transfer = (int)(e * c.birth_share);
		if (transfer > c.energy_birth_value) {
			transfer = c.energy_birth_value;
		}
		if (transfer < c.birth_min_energy) {
			transfer = c.birth_min_energy;
		}
		energy = e - transfer;

		Plant j = new Plant(w, nx(d), ny(d), brain == null ? null : brain.child(this));
		System.arraycopy(genome, 0, j.genome, 0, genome.length);//у потомка — своя копия генома
		j.type = t;
		j.energy = transfer;
		if (c.birth_health_share > 0) {//здоровье передается от родителя, а не появляется из ничего
			int hp = (int)(health * c.birth_health_share);
			health -= hp;
			j.health = hp > 1 ? hp : 1;
		}else {
			j.health = c.health_start_value;
		}
		j.cmd = cmd;
		j.gen = gen;
		j.head = d;//потомок смотрит от родителя
		j.dpar = (d + 2) % 4;
		j.color = color;
		j.ghash = ghash;
		w.grid[j.xpos][j.ypos] = j;
		link_before(j, it);//потомок встает в список перед родителем — в этом тике он не ходит
		Ledger.births++;
		if (PlantWorld.rand.nextDouble() < c.percent_mutation_child) {
			j.mutate(c.mutation_genes_birth);
		}
		return j;
	}
	void link_before(Plant j, ListIterator<IBot> it) {
		if (it == null) {//рождение вне хода (инструменты, тесты)
			w.objects.add(j);
			return;
		}
		it.previous();//курсор стоит после родителя: шаг назад, вставка перед ним, шаг вперед
		it.add(j);
		it.next();
	}
	//dirs[k], types[k] — направления и типы потомков; n — сколько. Возвращает число родившихся.
	int reproduce(int[] dirs, int[] types, int n, ListIterator<IBot> it) {
		PlantConfig c = w.c;
		int born = 0;
		int first_t = -1;
		int mask = 0;
		int ilive_mask = 0;
		Plant[] children = new Plant[n];
		for (int k = 0; k < n; k++) {
			if (born >= c.max_children_per_step) {
				break;
			}
			int t = types[k];
			int d = dirs[k];
			int child_t = c.birth_type_target == PlantConfig.BIRTH_PARENT ? ILIVE : t;
			Plant j = birth(d, child_t, it);
			if (j == null) {
				continue;
			}
			children[born] = j;
			born++;
			if (first_t < 0) {
				first_t = t;
			}
			mask |= 1 << d;
			if (child_t == ILIVE) {
				ilive_mask |= 1 << d;
			}else if (c.birth_type_target == PlantConfig.BIRTH_CHILD) {
				j.dmask = 1 << j.dpar;//тело-потомок кормит родителя
			}
		}
		if (born == 0) {
			return 0;
		}
		if (c.birth_type_target == PlantConfig.BIRTH_PARENT) {
			if (first_t == ILIVE) {//родитель остается ростком, все свободны
				dpar = -1;
				for (int k = 0; k < born; k++) {
					children[k].dpar = -1;
				}
			}else {
				type = first_t;
				dmask |= mask;
			}
		}else {
			type = TRANS;
			if (ilive_mask != 0) {
				dmask = ilive_mask;
			}else if (dpar >= 0) {
				dmask = 1 << dpar;
			}else {
				dmask = 0;
			}
		}
		return born;
	}
	//
	//ДЕЙСТВИЯ (PlantAction)
	//
	boolean move(int d) {//перемещение вместе с геномом; почва остается на месте
		int x = nx(d), y = ny(d);
		if (w.grid[x][y] != null) {
			return false;
		}
		w.grid[xpos][ypos] = null;
		xpos = x;
		ypos = y;
		w.grid[x][y] = this;
		return true;
	}
	boolean eat_dir(int d) {
		IBot o = at(d);
		if (o == null) {
			return false;
		}
		if (!(o instanceof Plant)) {//другой вид: ест только росток, КПД и обмен — из таблицы взаимодействий
			if (type != ILIVE) {
				return false;
			}
			double eff = eat_efficiency(o);
			if (eff <= 0) {
				return false;
			}
			double given = o.eaten_by(this, eff);
			long got = (long)(given * exchange(o));//энергия растений целая: дробь и обмен — статья журнала
			Ledger.in_exchange += got - given;
			add_energy(got);
			add_health(w.c.eat_health_gain);
			return true;
		}
		Plant j = (Plant) o;
		int tj = j.type;
		int ti = type;
		boolean ok = (ti == ILIVE && (tj == ILIVE || tj == IDEAD)) || (ti == ROOT && tj == IDEAD);
		if (!ok) {
			return false;
		}
		if (!is_enemy(j)) {
			return false;
		}
		long gain = (long) j.energy + j.ina + j.inb;
		boolean same = same_population(j);
		double eff = same ? w.c.eat_efficiency : eat_efficiency(j);//другая популяция — по таблице
		if (eff <= 0) {
			return false;
		}
		long to_eater = (long)(gain * eff);
		w.soil[j.xpos][j.ypos] += gain - to_eater;
		if (!same) {
			long got = (long)(to_eater * exchange(j));
			Ledger.in_exchange += got - to_eater;
			to_eater = got;
		}
		add_energy(to_eater);
		add_health(w.c.eat_health_gain);
		if (tj != IDEAD) {
			Ledger.die(Ledger.DIE_EATEN);
		}
		j.remove();
		return true;
	}
	boolean eat_all() {
		boolean ate = false;
		for (int d = 0; d < 8; d++) {
			if (eat_dir(d)) {
				ate = true;
			}
		}
		return ate;
	}
	boolean shift_soil(int d) {
		long e = (long) w.soil[xpos][ypos];
		if (e < 1) {
			return false;
		}
		if (e > w.c.soil_shift_max) {
			e = w.c.soil_shift_max;
		}
		w.soil[xpos][ypos] -= e;
		w.soil[nx(d)][ny(d)] += e;
		return true;
	}
	boolean can_move() {
		if (!w.c.enable_move) {
			return false;
		}
		if (type != ILIVE || dmask != 0) {
			return false;
		}
		if (w.c.move_only_free && dpar >= 0) {
			return false;
		}
		return true;
	}
	//
	//ПРОВЕРКИ THINK_NEW
	//
	boolean check_list(int k) {
		int h = head % 4;
		int left = (h + 3) % 4;
		int right = (h + 1) % 4;
		if (k == 0) {
			return type_at(left) != EMPTY;
		}
		if (k == 1) {
			return type_at(h) != EMPTY;
		}
		if (k == 2) {
			return type_at(right) != EMPTY;
		}
		if (k == 3) {
			return type_at(left) == EMPTY && type_at(h) == EMPTY && type_at(right) == EMPTY;
		}
		if (k == 4) {//хватит энергии на двоих потомков
			return energy >= 2 * (w.c.cost_birth + w.c.birth_min_energy);
		}
		if (k == 5) {//впереди чужой
			IBot o = at(h);
			return o != null && is_enemy(o);
		}
		if (k == 6) {//впереди свой
			IBot o = at(h);
			return is_alive_type(type_at(h)) && !is_enemy(o);
		}
		return false;
	}
	public boolean check(int k) {//проверки check_list — для нейросети и своих алгоритмов
		return check_list(k);
	}
	public int gcmd(int idx) {//команда idx активного гена
		PlantConfig c = w.c;
		int g = Math.floorMod(gen, c.genome_rows);
		return genome[g * c.genome_cols + Math.floorMod(idx, c.genome_cols)] & 0xFF;
	}
	int type_from_cmd(int cmd) {
		PlantConfig c = w.c;
		if (cmd < c.type_ilive_max) {
			return ILIVE;
		}
		if (cmd < c.type_leaf_max) {
			return LEAF;
		}
		if (cmd < c.type_trans_max) {
			return TRANS;
		}
		if (cmd < c.type_root_max) {
			return ROOT;
		}
		return EMPTY;
	}
	//
	//ЖИЗНЕННЫЙ ЦИКЛ
	//
	boolean body_step() {//работа тела; false — клетка умерла
		PlantConfig c = w.c;
		int t = type;
		int mask = dmask;
		if (mask != 0) {
			for (int d = 0; d < 4; d++) {
				int bit = 1 << d;
				if ((mask & bit) != 0) {
					IBot o = at(d);
					if (!is_alive_type(type_at(d)) || is_enemy(o)) {
						mask &= ~bit;
					}
				}
			}
			dmask = mask;
			if (mask == 0) {
				kill(Ledger.DIE_LOST);
				return false;
			}
		}
		//забор энергии из почвы
		int draw;
		if (t == ROOT) {
			draw = c.soil_draw_root;
		}else if (t == LEAF) {
			draw = c.soil_draw_leaf;
		}else {
			draw = c.soil_draw_trans;
		}
		long avail = (long) w.soil[xpos][ypos];
		if (avail < draw) {
			if (t != LEAF && c.soil_exhaust_kills) {
				kill(Ledger.DIE_SOIL);
				return false;
			}
			draw = avail > 0 ? (int) avail : 0;
		}
		w.soil[xpos][ypos] -= draw;
		long packet = draw;
		if (c.body_forward_energy && energy > c.body_keep_energy) {
			packet += energy - c.body_keep_energy;
			energy = c.body_keep_energy;
		}
		if (mask != 0) {
			int cnt = 0;
			for (int d = 0; d < 4; d++) {
				if ((mask & (1 << d)) != 0) {
					cnt++;
				}
			}
			long part = packet / cnt;
			long rest = packet - part * cnt;
			for (int d = 0; d < 4; d++) {
				if ((mask & (1 << d)) != 0) {
					send_energy_to((Plant) at(d), (int)(part + rest), c);
					rest = 0;
				}
			}
		}else {
			add_energy(packet);
		}
		if (t == LEAF) {
			int sun = c.leaf_sun_base;
			int hp = 0;
			for (int d = 0; d < 8; d++) {
				int tj = type_at(d);
				if (tj == EMPTY) {
					sun += c.leaf_sun_per_empty;
					hp += c.leaf_health_per_empty;
				}else if (tj == IDEAD) {
					sun += c.leaf_sun_per_corpse;
				}else if (tj == TRANS) {
					sun += c.leaf_sun_per_trans;
					hp += c.leaf_health_per_trans;
				}
			}
			if (sun > 0) {
				Ledger.in_plant_sun += sun;
				add_energy(sun);
			}
			add_health(hp);
		}
		if (t == LEAF || t == TRANS) {
			for (int d = 0; d < 8; d++) {
				int tj = type_at(d);
				if (tj == IDEAD) {
					health -= c.corpse_harm;
				}else if (tj == LEAF && t == TRANS && c.trans_leaf_bonus > 0) {
					Plant j = (Plant) at(d);
					int give = j.energy;
					if (give > c.trans_leaf_bonus) {
						give = c.trans_leaf_bonus;
					}
					if (give > 0) {
						j.energy -= give;
						add_energy(give);
					}
					add_health(c.trans_leaf_health);
				}
			}
			if (health < 1) {
				kill(Ledger.DIE_HEALTH);
				return false;
			}
		}
		if (t == ROOT) {
			for (int d = 0; d < 8; d++) {
				IBot o = at(d);
				if (o == null) {
					continue;
				}
				int tj = type_at(d);
				if (is_enemy(o)) {
					eat_dir(d);
				}else if (is_alive_type(tj) && c.root_feed > 0 && energy >= c.root_feed) {
					energy -= c.root_feed;
					((Plant) o).add_energy(c.root_feed);
				}
			}
		}
		return true;
	}
	void lifecycle() {
		PlantConfig c = w.c;
		if (killed != 0 || !is_alive_type(type)) {
			return;
		}
		age++;
		if (c.max_age > 0 && age > c.max_age) {
			kill(Ledger.DIE_AGE);
			return;
		}
		//энергия в пути доходит до клетки
		if (c.energy_transfer_delay >= 2) {
			add_energy(inb);
			inb = ina;
			ina = 0;
		}else if (c.energy_transfer_delay == 1) {
			add_energy((long) ina + inb);
			ina = 0;
			inb = 0;
		}
		if (c.toxic_soil_kills && w.soil[xpos][ypos] > c.max_energy_around) {
			kill(Ledger.DIE_TOXIC);
			return;
		}
		if (is_body_type(type)) {
			if (!body_step()) {
				return;
			}
		}
		if (c.mutate_alive_chance > 0 && PlantWorld.rand.nextDouble() < c.mutate_alive_chance) {
			mutate(1);
		}
		if (dpar == -1 || type == ILIVE) {
			take_energy(c.ilive_cost_step, STEP);
		}else if (c.body_cost_step > 0) {//содержание клетки дерева (у тела с родителем)
			take_energy(c.body_cost_step, STEP);
		}
	}
	void corpse_step() {
		age++;
		int e = energy;
		int d = w.c.corpse_decay < e ? w.c.corpse_decay : e;
		if (d > 0) {
			energy = e - d;
			w.org[xpos][ypos] += d;
		}
		if (energy <= 0) {
			remove();
		}
	}
	//органика переходит в энергию почвы; энергия почвы понемногу теряется — раз за шаг мира
	public static void mineralize(PlantWorld w) {
		PlantConfig c = w.c;
		if (c.organic_mineralize_ppm <= 0 && c.soil_decay_ppm <= 0) {
			return;
		}
		long lost = 0;
		for (int x = 0; x < w.W; x++) {
			double[] org = w.org[x];
			double[] soil = w.soil[x];
			for (int y = 0; y < w.H; y++) {
				if (c.organic_mineralize_ppm > 0) {
					long o = (long) org[y];//органика общая и дробная (боты Oxygen); доля — от целой части, как в biosim
					long m = o * c.organic_mineralize_ppm / 1000000;
					if (m > 0) {
						org[y] -= m;
						soil[y] += m;
					}
				}
				if (c.soil_decay_ppm > 0) {
					long e = (long) soil[y];
					long d = e * c.soil_decay_ppm / 1000000;
					if (d > 0) {
						soil[y] -= d;
						lost += d;
					}
				}
			}
		}
		Ledger.out_soil_decay += lost;
	}
	//
	//СОХРАНЕНИЕ (WorldIO, формат 2)
	//
	public void save(java.io.DataOutputStream out) throws java.io.IOException {
		out.writeInt(xpos);
		out.writeInt(ypos);
		for (int v : new int[]{type, energy, health, age, cmd, gen, head, dpar, dmask, ina, inb, color, ghash}) {
			out.writeInt(v);
		}
		out.writeInt(genome.length);
		out.write(genome);
	}
	public static Plant load(java.io.DataInputStream in, PlantWorld w, Brain<Plant> brain) throws java.io.IOException {
		Plant p = new Plant(w, in.readInt(), in.readInt(), brain);
		p.type = in.readInt();
		p.energy = in.readInt();
		p.health = in.readInt();
		p.age = in.readInt();
		p.cmd = in.readInt();
		p.gen = in.readInt();
		p.head = in.readInt();
		p.dpar = in.readInt();
		p.dmask = in.readInt();
		p.ina = in.readInt();
		p.inb = in.readInt();
		p.color = in.readInt();
		p.ghash = in.readInt();
		int n = in.readInt();
		if (n != p.genome.length) {
			throw new java.io.IOException("геном растения " + n + ", ожидалось " + p.genome.length);
		}
		in.readFully(p.genome);
		return p;
	}
	//
	//КОНТРАКТ IBot
	//
	public int Update(ListIterator<IBot> iterator) {//run_cells для одной клетки
		if (killed != 0) {
			return 0;
		}
		if (type == ILIVE) {
			update_commands(iterator);
			if (w.c.enable_lifecycle) {
				lifecycle();
			}
		}else if (is_body_type(type)) {
			if (w.c.enable_lifecycle) {
				lifecycle();
			}
		}else if (type == IDEAD) {
			corpse_step();
		}
		return 0;
	}
	public void update_commands(ListIterator<IBot> iterator) {
		if (brain != null) {
			brain.think(this, iterator);
		}
	}
	public Species species() {
		return Species.PLANT;
	}
	public boolean crowds() {//любая клетка растения, в том числе труп, занимает место
		return true;
	}
	public double ledger_energy() {
		return killed == 0 ? (double) energy + ina + inb : 0;
	}
	public double energy_value() {
		return (double) energy + ina + inb;
	}
	public double eaten_by(IBot eater, double efficiency) {//как eat_dir: остаток от КПД — в почву
		long gain = (long) energy + ina + inb;
		long to_eater = (long)(gain * efficiency);
		w.soil[xpos][ypos] += gain - to_eater;
		if (type != IDEAD) {
			Ledger.die(Ledger.DIE_EATEN);
		}
		remove();
		return to_eater;
	}
	public void die(int cause) {
		kill(cause);
	}
	public boolean same_kin(IBot other) {
		return other instanceof Plant && !is_enemy(other);
	}
	//палитра biosim по типам; вспышка — клетке идет энергия
	private static final int[] palette = {0x000000, 0xDCE6E6, 0xBE320A, 0xE6830A, 0x0AB01A, 0x400BC6, 0x808080};
	private static final int[] palette_flash = {0x000000, 0xFFFFFF, 0xD95430, 0xCF490B, 0x0CFA86, 0x326EFA, 0x808080};
	public int color(int draw_type) {
		if (draw_type == 2) {//энергия
			int v = (int) Math.min(255, (long) energy * 255 / Math.max(1, w.c.max_energy));
			return (v << 16) | (v << 8) | v;
		}
		if (draw_type == 9) {//популяции
			return w.pop != null ? w.pop.color.getRGB() & 0xFFFFFF : 0x808080;
		}
		if (draw_type == 3 || draw_type == 8) {//кланы
			return type == IDEAD ? palette[IDEAD] : color;
		}
		return (ina > 0 || inb > 0) ? palette_flash[type] : palette[type];
	}
	public String describe() {
		return String.format("%s %s [%d, %d] energy %d (+%d in transit), health %d, age %d, gen %d, head %d, parent dir %d, mask %d",
			species().title, type_names[type], xpos, ypos, energy, ina + inb, health, age, gen, head, dpar, dmask);
	}
}
