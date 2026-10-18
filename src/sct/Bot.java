package sct;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.ListIterator;
import java.util.function.Predicate;

public class Bot extends BotSuper {
	java.util.List<? extends IBot> objects;//список мира; боту нужен только чтобы передать потомкам
	static Random rand = new Random();//один на всех ботов: раньше создавался у каждого; зерно задается в Main
	public IBot[][] map;//общая сетка мира: в клетке любое существо
	//
	private int x;//позиция
	private int y;
	//
	public Color color;//основные параметры
	public Color clan_color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
	public double energy;
	public double age = Constant.max_age;//дробный, чтобы работал замедленный износ королевы и подчиненных
	public int memory = 0;
	public int type;
	public int[] commands = new int[Constant.mind_size];
	public int[][] genom_array = new int[Constant.mind_count][Constant.mind_size];
	public int genom_current_idx = 0;
	public int index = 0;
	public int rotate = rand.nextInt(8);
	private boolean[] chain = new boolean[8];
	//
	public int c_red = -1;//цвет в режиме отрисовки хищников
	public int c_green = -1;
	public int c_blue = -1;
	//
	public int state = 0;//тип бота
	public int pht_org_block = 0;//специализация: 1 - фотосинтез, 2 - переработка органики
	public int seed_time;//сколько лететь(если семечко)
	public int queen = 0;//0-обычная особь, 1-королева, 2-подчиненная
	public Bot queen_bot;
	public int queen_command = -1;// команда от королевы
	public int queen_marker = 0;
	public Brain<Bot> brain = null;//null — геном (update_commands); иначе свой алгоритм (Brains)
	public int kin_color = 0x808080;//цвет клана по геному (KinClans), режим отрисовки 8
	public int kin_id = -1;
	//
	private double[][] oxygen_map;
	private double[][] co2_map;
	private double[][] org_map;
	public Bot(int new_xpos, int new_ypos, Color new_color, double new_energy, int new_type, double[][] new_oxygen_map, double[][] new_co2_map, double[][] new_org_map, IBot[][] new_map, java.util.List<? extends IBot> new_objects) {
		xpos = new_xpos;
		ypos = new_ypos;
		x = new_xpos * Constant.size;
		y = new_ypos * Constant.size;
		color = new_color;
		energy = new_energy;
		objects = new_objects;
		map = new_map;
		oxygen_map = new_oxygen_map;
		co2_map = new_co2_map;
		org_map = new_org_map;
		type = new_type;
		//
		generate_genom();
//		for (int i = 0; i < 64; i++) {//заполняем мозг случайными числами
//			commands[i] = rand.nextInt(64);
//		}
	}
	public void generate_genom() {
		for (int i = 0; i < Constant.mind_count; i++) {
			for (int j = 0; j < Constant.mind_size; j++) {
				genom_array[i][j] = rand.nextInt(Constant.mind_max_value_command);
			}
		}
		genom_current_idx = 0;
		commands = genom_array[genom_current_idx];
		
	}
	public void set_current_genom(int idx) {
		genom_current_idx = idx % Constant.mind_count;
		commands = genom_array[genom_current_idx];//ссылка на свою строку генома: геном у каждого бота свой (см. multiply)
	}
	public void Draw(Graphics2D canvas, int draw_type, int zoom, int[] pos) {
		if (zoom == 0) {//зум x1
			canvas.setColor(get_color(draw_type));
			if (state == 1) {
				canvas.fillOval((int)(x + Math.ceil(Constant.size/4)), (int)(y + Math.ceil(Constant.size/4)), (int) Math.floor(Constant.size/2), (int) Math.floor(Constant.size/2));
			}else {
				canvas.fillRect(x, y, Constant.size, Constant.size);					
			}
			
			float stroke = (Constant.size / 3);
			int x_center = (int) Math.ceil(x + Constant.size / 2);
			int y_center = (int) Math.ceil(y + Constant.size / 2);
			int x_dir, y_dir;

			canvas.setColor(Color.black);
			if(draw_type == 7) {//отображение королевы
				canvas.setStroke(new BasicStroke(stroke));
				canvas.setColor(new Color(50,50,50));
				x_dir = (int) Math.ceil(x_center + (Constant.movelist[rotate][0] * Constant.size) / 2);
				y_dir = (int) Math.ceil(y_center + (Constant.movelist[rotate][1] * Constant.size)  / 2);
				canvas.drawLine(x_center, y_center, x_dir, y_dir);
				canvas.setStroke(new BasicStroke(1f));
				canvas.setColor(new Color(255,150,0));
				for(int i = 0; i < 8; i++) {
					if(chain[i]) {
						x_dir = (int) Math.ceil(x_center + (Constant.movelist[i][0] * Constant.size) / 2);
						y_dir = (int) Math.ceil(y_center + (Constant.movelist[i][1] * Constant.size)  / 2);
						canvas.drawLine(x_center, y_center, x_dir, y_dir);
					}
				}
			}else {
				x_dir = (int) Math.ceil(x_center + (Constant.movelist[rotate][0] * Constant.size) / 2);
				y_dir = (int) Math.ceil(y_center + (Constant.movelist[rotate][1] * Constant.size)  / 2);
				canvas.drawLine(x_center, y_center, x_dir, y_dir);
			}
			
		}else if (zoom == 1) {//зум x2.5
			int w = Constant.world_scale[0] * Constant.size / Constant.zoom_sizes[zoom];
			int h = Constant.world_scale[1] * Constant.size / Constant.zoom_sizes[zoom];
			if (xpos >= pos[0] - w / 2 && xpos < pos[0] + w / 2 && ypos >= pos[1] - h / 2 && ypos < pos[1] + h / 2) {
				canvas.setColor(get_color(draw_type));
				canvas.fillRect((xpos - pos[0] + w / 2) * 5, (ypos - pos[1] + h / 2) * 5, 5, 5);
			}
		}else if (zoom == 2) {//зум x5
			int w = Constant.world_scale[0] * Constant.size / Constant.zoom_sizes[zoom];
			int h = Constant.world_scale[1] * Constant.size / Constant.zoom_sizes[zoom];
			if (xpos >= pos[0] - w / 2 && xpos < pos[0] + w / 2 && ypos >= pos[1] - h / 2 && ypos < pos[1] + h / 2) {
				canvas.setColor(new Color(0, 0, 0));
				canvas.fillRect((xpos - pos[0] + w / 2) * 10, (ypos - pos[1] + h / 2) * 10, 10, 10);
				canvas.setColor(get_color(draw_type));
				canvas.fillRect((xpos - pos[0] + w / 2) * 10 + 1, (ypos - pos[1] + h / 2) * 10 + 1, 8, 8);
			}
		}
	}
	public int Update(ListIterator<IBot> iterator) {
		if (killed == 0) {
			if (state == 0) {//если бот
				if(queen == 0) {
					age -= (int)(oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1;
					energy -= Constant.energy_for_life;
					Ledger.out_life += Constant.energy_for_life;
				}else if (queen == 2){
					if(queen_bot == null || queen_bot.killed > 0) {
						die_with_organics(Ledger.DIE_QUEEN_LOST);
						return(0);
					}
					age -= (int)((oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1) * 0.9;
					energy -= Constant.energy_for_life * 0.9;
					Ledger.out_life += Constant.energy_for_life * 0.9;
					
				}else {
					age -= (int)((oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1) * 0.9;
					//age -= 1;
					energy -= Constant.energy_for_life * 2; // * 0.8;
					Ledger.out_life += Constant.energy_for_life * 2;
				}
				//
				int count = bot_count() + 1;//автоматическое деление и мозг
				if (count_oxygen() >= Constant.life_ox_coeff * count) {
					oxygen_map[xpos][ypos] -= Constant.life_ox_coeff * count;
					co2_map[xpos][ypos] += Constant.life_co2_coeff * count;
					update_commands(iterator);//если хватило кислорода, выполняем команды
					if (killed == 1) {//погиб при делении: дальше тик не продолжаем
						return(0);
					}
					if (energy >= Constant.energy_for_auto_multiply) {//автоматическое деление
						multiply(rotate, 0, 0, 0, false, iterator);
						if (killed == 1) {
							return(0);
						}
					}
				}
				update_chain();//обновление цепочек
				//
				if (energy > 1000 && queen < 1) {//ограничитель количества энергии
					Ledger.out_cap += energy - 1000;
					energy = 1000;
				}else if(energy > 2000) {
					Ledger.out_cap += energy - 2000;
					energy = 2000;
				}
				//
				if (age <= 0 || org_map[xpos][ypos] >= Constant.org_die_level || energy <= 0) {//смерть от старости или от органики
					die_with_organics(age <= 0 ? Ledger.DIE_AGE : (energy <= 0 ? Ledger.DIE_HUNGER : Ledger.DIE_ORGANICS));
					return(0);
				}
			}else if (state == 1){//если семечко
				if (org_map[xpos][ypos] >= Constant.org_die_level) {//умираем от переизбытка органики
					die_with_organics(Ledger.DIE_ORGANICS);
					double org_before = org_map[xpos][ypos];
					org_map[xpos][ypos] = Math.max(0, org_before - 0.1);
					Ledger.out_org_other += org_before - org_map[xpos][ypos];
					return(0);
				}
				//
				int res = move(rotate);//двигаемся
				if (res == 0) {//столкновение
					int[] pos = get_rotate_position(rotate);
					if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
						IBot hit = occupant(pos);
						if (hit != null) {
							die_with_organics(Ledger.DIE_COLLISION);//убить семечко
							if (seed_kills(hit)) {//другая популяция — по таблице взаимодействий
								hit.die(Ledger.DIE_COLLISION);//убить того, в кого попало
							}
							return(0);
						}
					}
				}
				//
				seed_time--;//уменьшаем счетчик
				if (seed_time <= 0) {//если время вышло, превращаемся в бота
					state = 0;
					rotate = rand.nextInt(8);
				}
			}
		}
		return(0);
	}
	public void update_commands(ListIterator<IBot> iterator) {//мозг: геном или свой алгоритм (brain)
		if (brain != null) {
			brain.think(this, iterator);
			return;
		}
		int max_mind = Constant.mind_size;
		int max_genom = Constant.mind_count;
		for (int i = 0; i < 5; i++) {
			if (killed == 1) {//погиб на предыдущей команде
				return;
			}
			int command = commands[index];
			if(queen_command > -1 && queen == 2 && queen_bot != null && queen_bot.killed == 0) {//пришел приказ от королевы и мы являемся подчиненным
				command = queen_command;
				queen_command = -1;
			}
			if (command == 0) {//повернуться
				if (on(BotAction.TURN)) {
					turn(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
			}else if (command == 1) {//сменить направление
				if (on(BotAction.SET_DIR)) {
					set_dir(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
			}else if (command == 2 || command == 3 || command == 4) {//фотосинтез
				if (on(BotAction.PHOTO)) {
					photosynthesis();
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 5) {//походить относительно
				if (on(BotAction.MOVE)) {
					move_cmd(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if(command == 6) {//походить абсолютно
				if (on(BotAction.MOVE)) {
					move_cmd(rotate);
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 7) {//атаковать относительно
				if (on(BotAction.ATTACK)) {
					attack_cmd(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 8) {//атаковать абсолютно
				if (on(BotAction.ATTACK)) {
					attack_cmd(rotate);
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 9) {//посмотреть относительно
				int rot = commands[(index + 1) % max_mind] % 8;
				index = commands[(index + 2 + see(rot)) % max_mind];
			}else if (command == 10) {//посмотреть абсолютно
				index = commands[(index + 1 + see(rotate)) % max_mind];
			}else if (command == 11 | command == 12) {//отдать ресурсы относительно
				if (on(BotAction.GIVE)) {
					give(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 13 | command == 14) {//отдать ресурсы абсолютно
				if (on(BotAction.GIVE)) {
					give(rotate);
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 15) {//сколько у меня энергии
				int ind = commands[(index + 1) % max_mind] * 15;
				if (energy >= ind) {
					index = commands[(index + 2) % max_mind];
				}else {
					index = commands[(index + 3) % max_mind];
				}
			//16, 17 — свободны (были проверки сектора по y; при замкнутом мире и отключенных секторах смысла не имели)
			}else if (command == 18) {//поделиться относительно
				int new_type = commands[(index + 2) % max_mind] % 8;
				if (on(BotAction.DIVIDE)) {
					multiply(commands[(index + 1) % max_mind] % 8, 0, 0, new_type, false, iterator);
				}
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 19) {//поделиться абсолютно
				int new_type = commands[(index + 1) % max_mind] % 8;
				if (on(BotAction.DIVIDE)) {
					multiply(rotate, 0, 0, new_type, false, iterator);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 20) {//какая моя позиция x
				double ind = commands[(index + 1) % max_mind] / (double) Constant.mind_max_value_command;//доля 0..1; целочисленное деление давало всегда 0
				if (xpos * 1.0 / Constant.world_scale[0] >= ind) {
					index = commands[(index + 2) % max_mind];
				}else {
					index = commands[(index + 3) % max_mind];
				}
			}else if (command == 21) {//какая моя позиция y
				double ind = commands[(index + 1) % max_mind] / (double) Constant.mind_max_value_command;//доля 0..1; целочисленное деление давало всегда 0
				if (ypos * 1.0 / Constant.world_scale[1] >= ind) {
					index = commands[(index + 2) % max_mind];
				}else {
					index = commands[(index + 3) % max_mind];
				}
			}else if (command == 22) {//какой мой возраст
				int ind = commands[(index + 1) % max_mind] * 15;
				if (age >= ind) {
					index = commands[(index + 2) % max_mind];
				}else {
					index = commands[(index + 3) % max_mind];
				}
			}else if (command == 23) {//равномерное распределение ресурсов относительно
				if (on(BotAction.SHARE)) {
					unif_distrib(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 24) {//равномерное распределение ресурсов абсолютно
				if (on(BotAction.SHARE)) {
					unif_distrib(rotate);
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 25) {//безусловный переход
				index = commands[(index + 1) % max_mind];
			}else if (command == 26) {//сколько кислорода
				//порог в диапазоне 0..ox_render_maximum_coeff: реальный кислород ~0.04, целочисленное деление давало всегда 0
				double ind = commands[(index + 1) % max_mind] / (double) Constant.mind_max_value_command * Constant.ox_render_maximum_coeff;
				if (oxygen_map[xpos][ypos] >= ind) {
					index = commands[(index + 2) % max_mind];
				}else {
					index = commands[(index + 3) % max_mind];
				}
			}else if (command == 27 || command == 28) {//переработка органики под собой
				if (on(BotAction.RECYCLE)) {
					recycle_cmd(-1, (int)(commands[(index + 1) % max_mind] * Constant.org_recycle_coeff));
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 29 || command == 30) {//переработка органики перед собой относительно
				if (on(BotAction.RECYCLE)) {
					recycle_cmd(commands[(index + 1) % max_mind] % 8, (int)(commands[(index + 2) % max_mind] * Constant.org_recycle_coeff));
				}
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 31 || command == 32) {//переработка органики перед собой абсолютно
				if (on(BotAction.RECYCLE)) {
					recycle_cmd(rotate, (int)(commands[(index + 1) % max_mind] * Constant.org_recycle_coeff));
				}
				index += 2;
				index %= max_mind;
				break;
//			}else if (command == 33) {//сколько органики подо мной
//				int ind = commands[(index + 1) % max_mind];
//				if (org_map[xpos][ypos] >= ind * 5) {
//					index = commands[(index + 2) % max_mind];
//				}else {
//					index = commands[(index + 3) % max_mind];
//				}
			}else if (command == 33) {//сколько органики подо мной
				int ind = commands[(index + 1) % max_mind];
				int d = commands[(index + 2) % max_mind];
				if (org_map[xpos][ypos] >= ind * d) {
					index = commands[(index + 3) % max_mind];
				}else {
					index = commands[(index + 4) % max_mind];
				}
			}else if (command == 34) {//сколько органики передо мной относительно
				int rot = commands[(index + 1) % max_mind] % 8;
				int ind = commands[(index + 2) % max_mind];
				index = commands[(index + 3 + see_org(rot, ind)) % max_mind];
			}else if (command == 35) {//сколько органики передо мной абсолютно
				int ind = commands[(index + 1) % max_mind];
				index = commands[(index + 2 + see_org(rotate, ind)) % max_mind];
			}else if (command == 36) {//какое мое направление
				int ind = commands[(index + 1) % max_mind] % 8;
				if (rotate > ind) {
					index = commands[(index + 2) % max_mind];
				}else if (rotate == ind) {
					index = commands[(index + 3) % max_mind];
				}else {
					index = commands[(index + 4) % max_mind];
				}
			}else if (command == 37) {//установить направление в случайное
				if (on(BotAction.RANDOM_DIR)) {
					random_dir();
				}
				index += 1;
				index %= max_mind;
			}else if (command == 38) {//сколько ботов вокруг
				int ind = commands[(index + 1) % max_mind] % 8;
				int count = bot_count();
				if (count > ind) {
					index = commands[(index + 2) % max_mind];
				}else if (count == ind) {
					index = commands[(index + 3) % max_mind];
				}else {
					index = commands[(index + 4) % max_mind];
				}
			}else if (command == 39) {//стрелять семечком относительно
				int rot = commands[(index + 1) % max_mind] % 8;
				int time = commands[(index + 2) % max_mind] % 16 + 1;
				int new_type = commands[(index + 3) % max_mind] % 8;
				if (on(BotAction.SEED)) {
					multiply(rot, 1, time, new_type, false, iterator);
				}
				index += 4;
				index %= max_mind;
				break;
			}else if (command == 40) {//стрелять семечком абсолютно
				int time = commands[(index + 1) % max_mind] % 16 + 1;
				int new_type = commands[(index + 2) % max_mind] % 8;
				if (on(BotAction.SEED)) {
					multiply(rotate, 1, time, new_type, false, iterator);
				}
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 41) {//какой у меня тип
				index = commands[(index + 1 + type) % max_mind];
			}else if (command == 42) {//добавление в цепочку относительно
				int new_type = commands[(index + 2) % max_mind] % 8;
				if (on(BotAction.CHAIN_DIVIDE)) {
					multiply(commands[(index + 1) % max_mind] % 8, 0, 0, new_type, true, iterator);
				}
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 43) {//добавление в цепочку абсолютно
				int new_type = commands[(index + 1) % max_mind] % 8;
				if (on(BotAction.CHAIN_DIVIDE)) {
					multiply(rotate, 0, 0, new_type, true, iterator);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 44) {//сколько у меня связей в цепочке
				int c = count_chains();
				int ind = commands[(index + 1) % max_mind] % 8;
				if (c > ind) {//
					index = commands[(index + 2) % max_mind];
				}else if (c < ind) {//
					index = commands[(index + 3) % max_mind];
				}else {//
					index = commands[(index + 4) % max_mind];
				}
			}else if (command == 45) {//присоеденить соседа к цепочке относительно
				if (on(BotAction.CHAIN_LINK)) {
					add_neighbour_to_chain(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 46) {//присоеденить соседа к цепочке абсолютно
				if (on(BotAction.CHAIN_LINK)) {
					add_neighbour_to_chain(rotate);
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 47) {//смена генома
				int index_genom = commands[(index + 1) % max_mind] % max_genom;
				set_current_genom(index_genom);
				index = 0;
				//System.out.println("смена генома: "+index_genom);
			}else if (command == 48) {//проверка текущего генома
				index = commands[(index + 1 + genom_current_idx) % max_mind];
			}else if(command == 49){//королева. прославить свою королеву
				if(queen == 2 && queen_bot != null) {
					if (on(BotAction.QUEEN_CALL)) {
						set_queen(queen_bot,this,1);
					}
					index += 1;//без сдвига мозг выполнял эту команду вечно
					index %= max_mind;
					break;
				}else {
					index += command;
					index %= max_mind;
				}
			}else if(command == 50) {//особь становится королевой при достаточном количестве связей
				int c = count_chains();
				if(c >= 5 && queen == 0 || queen == 1) {//если уже королева, то делаем повторный призыв подчиненных
					//queen = 1;
					//System.out.println("Рождение королевы. Связей: "+c);
					if (on(BotAction.QUEEN_BECOME)) {
						set_queen(this,this,0);
					}
					index = commands[(index + 1) % max_mind];
				}else {
					index = commands[(index + 2) % max_mind];
				}
				break;
			}else if(command == 51) {//проверка своего статуса королевы
				int check_queen = commands[(index + 1) % max_mind] % 3;
				if(queen == check_queen) {
					index = commands[(index + 2) % max_mind];
					//System.out.println("Королева. Проверка своего статуса: check_queen="+check_queen+" index="+index);
				}else {
					index = commands[(index + 3) % max_mind];
				}
			}else if(command == 52){//приказ королевы в дистанции
				if(queen == 1) {
					int do_command = commands[(index + 1) % max_mind] % 50;
					int distance = commands[(index + 2) % max_mind] % Constant.queen_max_radius + 1;
					//System.out.println("Королева. Приказ королевы в дистанции: do_command="+do_command+" distance="+distance);
					if (on(BotAction.QUEEN_ORDER)) {
						queen_orders(distance, do_command, 0);
					}
					index += 3;//команда + 2 параметра
					index %= max_mind;
					break;
				}else {
					index += command;
					index %= max_mind;
				}
			}else if(command == 53){//приказ королевы за дистанцией
				if(queen == 1) {
					int do_command = commands[(index + 1) % max_mind] % 50;
					int distance = commands[(index + 2) % max_mind] % Constant.queen_max_radius;
					//System.out.println("Королева. Приказ королевы за дистанцией: do_command="+do_command+" distance="+distance);
					if (on(BotAction.QUEEN_ORDER)) {
						queen_orders(distance, do_command, 1);
					}
					index += 3;//команда + 2 параметра
					index %= max_mind;
					break;
				}else {
					index += command;
					index %= max_mind;
				}
			}else if(command == 54){//приказ королевы перед дистанцией
				if(queen == 1) {
					int do_command = commands[(index + 1) % max_mind] % 50;
					int distance = commands[(index + 2) % max_mind] % Constant.queen_max_radius + 2;
					//System.out.println("Королева. Приказ королевы перед дистанцией: do_command="+do_command+" distance="+distance);
					if (on(BotAction.QUEEN_ORDER)) {
						queen_orders(distance, do_command, -1);
					}
					index += 3;//команда + 2 параметра
					index %= max_mind;
					break;
				}else {
					index += command;
					index %= max_mind;
				}
			}else if(command == 55){//королева. посчитать подчиненных (пока заглушка)
				if(queen == 1) {
					index += 1;
					index %= max_mind;
					break;
				}else {
					index += command;
					index %= max_mind;
				}
			}else {
				index += command;
				index %= max_mind;
			}
		}
	}
	//
	//ДЕЙСТВИЯ ТЕЛА (BotAction). Код перенесен из update_commands без изменений.
	//
	public boolean on(BotAction a) {//включено ли действие (галочка популяции); выключенное — команда без эффекта
		return pop != null ? pop.on(a.ordinal()) : a.enabled();
	}
	public double oxygen() {//среда в клетке бота — для датчиков и своих алгоритмов
		return oxygen_map[xpos][ypos];
	}
	public double co2() {
		return co2_map[xpos][ypos];
	}
	public double organics(int cx, int cy) {
		return org_map[cx][cy];
	}
	public int dir(int d) {//направление действия: 0..7 или BotAction.FORWARD — куда смотрю
		return d == BotAction.FORWARD ? rotate : Constant.mod(d, 8);
	}
	public void turn(int angle) {//повернуть на угол 0..7
		rotate += angle;
		rotate %= 8;
	}
	public void set_dir(int d) {
		rotate = d;
	}
	public void random_dir() {
		rotate = rand.nextInt(8);
	}
	public void photosynthesis() {//фотосинтез: нужен CO2, специализация — не переработчик
		int sector = Constant.sector(ypos);
		//if (sector <= 7 && pht_org_block != 2 && count_co2() >= Constant.pht_co2_coeff) {
		if (pht_org_block != 2 && count_co2() >= Constant.pht_co2_coeff) {
			pht_org_block = 1;
			double pe = photo_energy(sector);//один вызов: photo_energy расходует органику вокруг
			energy += pe;
			Ledger.in_sun += pe;
			go_green();
			oxygen_map[xpos][ypos] += Constant.pht_ox_coeff * pe;
//			if (oxygen_map[xpos][ypos] > 1) {
//				oxygen_map[xpos][ypos] = 1;
//			}
			co2_map[xpos][ypos] -= Constant.pht_co2_coeff;
			if (co2_map[xpos][ypos] < 0) {
				co2_map[xpos][ypos] = 0;
			}
		}
	}
	public void move_cmd(int rot) {//шаг: кислород по тесноте, без связей цепочки
		int count = bot_count() + 1;
		if (count_oxygen() >= Constant.move_ox_coeff * count && count_chains() == 0) {
			oxygen_map[xpos][ypos] -= Constant.move_ox_coeff * count;
			int sens = move(rot);
			if (sens == 1) {
				energy -= Constant.energy_for_move;
				Ledger.out_move += Constant.energy_for_move;
			}
		}
	}
	public void attack_cmd(int rot) {//атака: кислород по тесноте
		int count = bot_count() + 1;
		if (count_oxygen() >= Constant.attack_ox_coeff * count) {
			oxygen_map[xpos][ypos] -= Constant.attack_ox_coeff * count;
			attack(rot);
		}
	}
	public void recycle_cmd(int rot, int amount) {//переработка органики: rot -1 — под собой; специализация — не фотосинтетик
		if (pht_org_block != 1) {
			pht_org_block = 2;
			if (rot < 0) {
				recycle_organics_under(amount);
			}else {
				recycle_organics(rot, amount);
			}
		}
	}
	//
	//КОМАНДЫ
	//
	public void add_neighbour_to_chain(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			Bot n = bot_at(pos);
			if (n != null && n.state == 0 && can_chain(n)) {
				chain[rot] = true;
				n.chain[(rot + 4) % 8] = true;
			}
		}
	}
	//
	public int move(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (occupant(pos) == null) {
				IBot self = map[xpos][ypos];
				map[xpos][ypos] = null;
				xpos = pos[0];
				ypos = pos[1];
				x = xpos * Constant.size;
				y = ypos * Constant.size;
				map[xpos][ypos] = self;
				return(1);
			}else {
				return(0);
			}
		}
		return(0);
	}
	//
	public void attack(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			IBot target = occupant(pos);
			if (target != null) {
				Bot victim = bot_at(pos);
				if (victim == null || !same_population(victim)) {//другая популяция — по таблице взаимодействий
					eat_other(target);
				}else if (victim != null) {
					energy += victim.energy;
					victim.energy = 0;//энергию забрал хищник: в органику уходит только тело
					victim.die_with_organics(Ledger.DIE_EATEN);
					go_red();
				}
			}
		}
	}
	//
	//КОРОЛЕВЫ
	//
	//обход колонии по связям цепочки в ширину: каждая клетка посещается один раз,
	//distance — кратчайшее число шагов по цепочке от start, parent — от кого пришли
	private interface ChainVisitor {
		void visit(Bot b, Bot parent, int distance);
	}
	private void walk_chain(Bot start, int start_distance, int max_distance, Predicate<Bot> pass, ChainVisitor visitor) {
		ArrayDeque<Bot> queue = new ArrayDeque<Bot>();
		HashMap<Bot, Integer> dist = new HashMap<Bot, Integer>();
		queue.add(start);
		dist.put(start, start_distance);
		while (!queue.isEmpty()) {
			Bot b = queue.poll();
			int d = dist.get(b);
			if (d >= max_distance) {
				continue;
			}
			for (int i = 0; i < 8; i++) {
				int[] pos = b.get_rotate_position(i);
				Bot n = b.bot_at(pos);
				if (n == null || n.state != 0 || n.killed != 0 || dist.containsKey(n)) {
					continue;
				}
				if (!b.chain[i] && !n.chain[(i + 4) % 8]) {//не связаны цепочкой
					continue;
				}
				if (!pass.test(n)) {
					continue;
				}
				dist.put(n, d + 1);
				visitor.visit(n, b, d + 1);
				queue.add(n);
			}
		}
	}
	//
	private void pass_crown() {//наследница — соседняя подчиненная с наибольшей энергией, колония переходит к ней
		Bot[] heir_box = {null};
		walk_chain(this, 0, 1, n -> n.queen == 2 && n.queen_bot == this, (n, parent, d) -> {
			if (heir_box[0] == null || n.energy > heir_box[0].energy) {
				heir_box[0] = n;
			}
		});
		Bot heir = heir_box[0];
		if (heir == null) {
			return;
		}
		Bot old_queen = this;
		heir.queen = 1;
		heir.queen_bot = null;
		heir.queen_command = -1;
		walk_chain(heir, 0, Constant.queen_max_radius, n -> n.queen == 2 && n.queen_bot == old_queen, (n, parent, d) -> {
			n.queen_bot = heir;
		});
		//подчиненные старой королевы дальше радиуса от наследницы остаются без королевы и гибнут (Update)
	}
	//
	public void queen_mark(Graphics2D g) {//королева поверх линий и цепочек: белая рамка и синий центр
		g.setColor(Color.white);
		g.fillRect(x - 2, y - 2, Constant.size + 4, Constant.size + 4);
		g.setColor(new Color(0, 50, 250));
		g.fillRect(x - 1, y - 1, Constant.size + 2, Constant.size + 2);
	}
	//
	public void queen_draw(Graphics2D g) {//линии от каждой подчиненной к той клетке, через которую она связана с королевой
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(40, 40, 120, 130));//полупрозрачные, чтобы не сливались с королевой
		walk_chain(this, 0, Constant.queen_max_radius, n -> n.queen == 2 && n.queen_bot == this, (n, parent, d) -> {
			if (Math.abs(n.xpos - parent.xpos) > 1 || Math.abs(n.ypos - parent.ypos) > 1) {
				return;//связь через край замкнутого мира — линию через весь экран не рисуем
			}
			int half = Constant.size / 2;
			g.drawLine(n.x + half, n.y + half, parent.x + half, parent.y + half);
		});
	}
	//
	public void set_queen(Bot queen_bot, Bot b, int distance) {//b — откуда начинается призыв, distance — его расстояние до королевы
		if (distance < 1) {//дистанция 0 — это сама королева
			b.queen = 1;
			b.queen_bot = null;
		}
		//вербуются только связанные цепочкой свободные клетки; через своих подчиненных призыв проходит дальше
		walk_chain(b, distance, Constant.queen_max_radius, n -> n.queen == 0 || (n.queen == 2 && n.queen_bot == queen_bot), (n, parent, d) -> {
			n.queen = 2;
			n.queen_bot = queen_bot;
		});
	}
	//
	public void queen_orders(int distance_todo, int command, int compare) {//приказ подчиненным: compare 0 — на дистанции, 1 — за ней, -1 — перед ней
		walk_chain(this, 0, Constant.queen_max_radius, n -> n.queen == 2 && n.queen_bot == this, (n, parent, d) -> {
			if (Integer.signum(Integer.compare(d, distance_todo)) == compare) {
				n.queen_command = command;
			}
		});
	}
	//
	public int see(int rot) {//мир замкнут, границы нет
		int[] pos = get_rotate_position(rot);
		IBot o = occupant(pos);
		Bot b = bot_at(pos);
		if (org_map[pos[0]][pos[1]] >= Constant.org_die_level) {
			return(3);//если переизбыток органики
		}else if (o != null && b == null) {
			return(o.species().see_code);//существо другого вида: свой код (7 — растение, 8 — нейро-бот)
		}else if (b == null) {
			return(1);//если ничего
		}else if (b.state != 0) {
			return(0);//если семечко
		}else if (b.queen == 1) {
			return(5);//королева
		}else if (b.queen == 2) {
			return(6);//подчиненная
		}else if (count_different_genom(genom_array, b.genom_array) <= Constant.max_different_genom) {
			return(2);//родственник
		}else {
			return(4);//чужой
		}
	}
	//
	public void give(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			Bot relative = bot_at(pos);
			if (relative != null && relative.state == 0 && can_share(relative)) {
				if (relative.killed == 0) {
					relative.energy += energy / 4;
					energy -= energy / 4;
				}
			}
		}
	}
	//
	public void unif_distrib(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			Bot relative = bot_at(pos);
			if (relative != null && relative.state == 0 && can_share(relative)) {
				if (relative.killed == 0) {
					double enr = relative.energy + energy;
					relative.energy = enr / 2;
					energy = enr / 2;
				}
			}
		}
	}
	//
	public void recycle_organics_under(int org) {
		recycle_organics_at(xpos, ypos, org);
	}
	//
	public void recycle_organics(int rot, int org) {
		int[] pos = get_rotate_position(rot);
		recycle_organics_at(pos[0], pos[1], org);
	}
	//
	private void recycle_organics_at(int px, int py, int org) {//переработка органики в клетке: органика переходит в энергию 1:1
		//раньше проверялось org, а вычиталось org*8 (органика уходила в минус),
		//а в ветке "забрать всё" энергия начислялась x8 от изъятой органики
		double take = Math.min(org * 8.0, org_map[px][py]);
		if (take <= 0) {
			return;
		}
		double gas = take / 8.0;//газообмен — как раньше, от объема до умножения
		if (count_oxygen() >= gas * Constant.org_recycle_ox_coeff) {
			oxygen_map[xpos][ypos] = Math.max(0, oxygen_map[xpos][ypos] - gas * Constant.org_recycle_ox_coeff);
			co2_map[xpos][ypos] += gas * Constant.org_recycle_co2_coeff;
			energy += take;
			org_map[px][py] -= take;
			go_yellow();
		}
	}
	//
	public int see_org(int rot, int ind) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (org_map[pos[0]][pos[1]] >= ind * 15) {
				return(0);
			}else {
				return(1);
			}
		}else {
			return(2);
		}
	}
	//
	public int count_different_genom(int[][] gen1, int[][] gen2) {
		int diff_count = 0;
		for (int i = 0; i < gen1.length; i++) {
			for(int j = 0; j < gen1[i].length; j++) {
				if(gen1[i][j] != gen2[i][j]) {
					diff_count++;
				}				
			}
		}		
		return diff_count;
	}
	//
	public int count_different_genom(int[] gen1, int[] gen2) {
		int diff_count = 0;
		for (int i = 0; i < gen1.length; i++) {
			if(gen1[i] != gen2[i]) {
				diff_count++;
			}
		}
		return diff_count;
	}
	//
	public void multiply(int rot, int seed, int time, int new_type, boolean ch, ListIterator<IBot> iterator) {
		int max_mind = Constant.mind_size;
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (occupant(pos) == null) {
				energy -= Constant.energy_for_multiply;
				if (energy <= 0) {
					Ledger.out_birth_fail += Constant.energy_for_multiply;//потомок не родился, цена деления потеряна
					die_with_organics(Ledger.DIE_HUNGER);
				}else {
					Color new_color;
					if (rand.nextInt(800) == 0) {//немного меняем(с шансом 1/800 - полностью)
						new_color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
					}else {
						new_color = new Color(Constant.border(color.getRed() + rand.nextInt(-12, 13), 255, 0), Constant.border(color.getGreen() + rand.nextInt(-12, 13), 255, 0), Constant.border(color.getBlue() + rand.nextInt(-12, 13), 255, 0));
					}
					//глубокая копия: clone() двумерного массива копирует только внешний массив,
					//строки оставались общими и мутация потомка меняла геном родителя и всего клана
					int[][] new_brain = new int[Constant.mind_count][];
					for (int i = 0; i < Constant.mind_count; i++) {
						new_brain[i] = genom_array[i].clone();
					}
//					int[] new_brain = new int[64];
//					for (int i = 0; i < 64; i++) {
//						new_brain[i] = commands[i];
//					}
//					if (rand.nextInt(4) == 0) {//мутация
//						new_brain[rand.nextInt(64)] = rand.nextInt(64);
//					}
					boolean mutated = false;
					if (rand.nextInt(100) < Constant.mutate_chance_percent) {//мутация
						new_brain[rand.nextInt(Constant.mind_count)][rand.nextInt(max_mind)] = rand.nextInt(Constant.mind_max_value_command);
						mutated = true;
						Ledger.mutations++;
					}
					
					Bot new_bot = new Bot(
						pos[0],
						pos[1],
						new_color,
						energy / 2,
						new_type,
						oxygen_map,
						co2_map,
						org_map,
						map,
						objects
					);
					energy /= 2;
//					new_bot.commands = new_brain;
					new_bot.genom_array = new_brain;
					new_bot.set_current_genom(genom_current_idx);//потомок продолжает с того же генома, что и родитель
					new_bot.brain = brain == null ? null : brain.child(this);
					new_bot.pop = pop;
					//цвет клана наследуется и чуть сдвигается при каждой мутации — видны родственные группы
					new_bot.clan_color = mutated ? drift_color(clan_color, 10) : clan_color;
					//
					if (seed == 1) {
						new_bot.state = 1;
						new_bot.seed_time = time;
						new_bot.rotate = rot;
					}else if (ch || queen > 0) {//у королевы и подчиненных потомок сразу в цепочке, связь с обеих сторон
						chain[rot] = true;
						new_bot.chain[(rot + 4) % 8] = true;
					}
					//
					iterator.add(new_bot);
					Ledger.births++;
					map[pos[0]][pos[1]] = new_bot;
				}
			}
		}
	}
	//
	//СЕТКА И КОНТРАКТ IBot
	//
	public IBot occupant(int[] pos) {//любое существо в клетке
		return map[pos[0]][pos[1]];
	}
	public Bot bot_at(int[] pos) {//бот в клетке (Oxygen или нейро), иначе null
		IBot o = map[pos[0]][pos[1]];
		return o instanceof Bot ? (Bot) o : null;
	}
	public Species species() {
		return Species.OXYGEN_BOT;
	}
	private void eat_other(IBot target) {//съесть существо другой популяции: КПД и обмен из таблицы
		double eff = eat_efficiency(target);
		if (eff <= 0) {
			return;
		}
		double given = target.eaten_by(this, eff);
		double got = given * exchange(target);
		Ledger.in_exchange += got - given;
		energy += got;
		go_red();
	}
	public boolean crowds() {//семечко соседом не считается — как было в bot_count
		return state == 0;
	}
	public double ledger_energy() {//энергия и тело (energy_for_multiply), см. Ledger
		return killed == 0 ? energy + Constant.energy_for_multiply : 0;
	}
	public double energy_value() {
		return energy;
	}
	public double eaten_by(IBot eater, double efficiency) {//съевшему — доля энергии, остаток и тело — в органику
		double given = energy * efficiency;
		energy -= given;
		die_with_organics(Ledger.DIE_EATEN);
		return given;
	}
	public void die(int cause) {
		die_with_organics(cause);
	}
	public boolean same_kin(IBot other) {
		return other instanceof Bot && other.species() == species() && same_population(other)
			&& count_different_genom(genom_array, ((Bot) other).genom_array) <= Constant.max_different_genom;
	}
	public int color(int draw_type) {
		return get_color(draw_type).getRGB() & 0xFFFFFF;
	}
	public String describe() {
		return String.format("%s [%d, %d] energy %.1f, age %.1f, type %d, queen %d, chains %d, genom %d, index %d",
			species().title, xpos, ypos, energy, age, type, queen, count_chains(), genom_current_idx, index);
	}
	//
	//СОХРАНЕНИЕ (WorldIO)
	//
	int load_queen_ref = -1;//номер королевы в файле, связывается после чтения всех ботов
	public void save(java.io.DataOutputStream out, java.util.Map<Bot, Integer> ids) throws java.io.IOException {
		out.writeInt(xpos);
		out.writeInt(ypos);
		out.writeInt(color.getRGB());
		out.writeInt(clan_color.getRGB());
		out.writeDouble(energy);
		out.writeDouble(age);
		out.writeInt(memory);
		out.writeInt(type);
		for (int[] g : genom_array) {
			for (int v : g) {
				out.writeInt(v);
			}
		}
		out.writeInt(genom_current_idx);
		out.writeInt(index);
		out.writeInt(rotate);
		for (boolean c : chain) {
			out.writeBoolean(c);
		}
		out.writeInt(c_red);
		out.writeInt(c_green);
		out.writeInt(c_blue);
		out.writeInt(state);
		out.writeInt(pht_org_block);
		out.writeInt(seed_time);
		out.writeInt(queen);
		Integer q = queen_bot == null ? null : ids.get(queen_bot);
		out.writeInt(q == null ? -1 : q);
		out.writeInt(queen_command);
		out.writeInt(queen_marker);
	}
	public static Bot load(java.io.DataInputStream in, double[][] oxygen_map, double[][] co2_map, double[][] org_map, IBot[][] map, java.util.List<? extends IBot> objects) throws java.io.IOException {
		int bx = in.readInt();
		int by = in.readInt();
		Color c = new Color(in.readInt());
		Bot b = new Bot(bx, by, c, 0, 0, oxygen_map, co2_map, org_map, map, objects);
		b.clan_color = new Color(in.readInt());
		b.energy = in.readDouble();
		b.age = in.readDouble();
		b.memory = in.readInt();
		b.type = in.readInt();
		for (int[] g : b.genom_array) {
			for (int i = 0; i < g.length; i++) {
				g[i] = in.readInt();
			}
		}
		b.set_current_genom(in.readInt());
		b.index = in.readInt();
		b.rotate = in.readInt();
		for (int i = 0; i < 8; i++) {
			b.chain[i] = in.readBoolean();
		}
		b.c_red = in.readInt();
		b.c_green = in.readInt();
		b.c_blue = in.readInt();
		b.state = in.readInt();
		b.pht_org_block = in.readInt();
		b.seed_time = in.readInt();
		b.queen = in.readInt();
		b.load_queen_ref = in.readInt();
		b.queen_command = in.readInt();
		b.queen_marker = in.readInt();
		return b;
	}
	//
	//ТЕХНИЧЕСКИЕ ФУНКЦИИ
	//
	public double count_oxygen() {
		double sum = oxygen_map[xpos][ypos] + co2_map[xpos][ypos];
		if (sum <= 0) {//оба газа кончились: без проверки 0/0 = NaN и мозг молча отключается
			return(0);
		}
		double ox = oxygen_map[xpos][ypos] * (oxygen_map[xpos][ypos] / sum);
		return(ox);
	}
	//
	public double count_co2() {
		double sum = oxygen_map[xpos][ypos] + co2_map[xpos][ypos];
		if (sum <= 0) {
			return(0);
		}
		double co2 = co2_map[xpos][ypos] * (co2_map[xpos][ypos] / sum);
		return(co2);
	}
	//
	public double photo_energy(int sector) {
		int distance = 2;
		int count = bot_count(2);
		int count_org = 0;
		//в плотной колонии (count > 9*distance) множитель уходил в минус и фотосинтез отнимал энергию
		double n = Constant.border((9*distance - count) * Constant.pht_neighbours_coeff, 1.0, 0.0);
		for (int i = 0; i < 8; i++) {
			for (int d = 1; d <= 3; d++) { //дистанция 
				int[] pos = get_rotate_position(i,d);
				if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
					if (org_map[pos[0]][pos[1]] > 0) {
						count_org++;
						double org_before = org_map[pos[0]][pos[1]];
						org_map[pos[0]][pos[1]] = Math.max(0, org_before - 0.0001);
						Ledger.out_org_other += org_before - org_map[pos[0]][pos[1]];
					}
				}				
			}
		}		
		//TODO: sector OFF
		//double enr = Constant.pht_energy_list[sector] * (org_map[xpos][ypos] / Constant.pht_coeff) * n;
		double enr = 9 * (org_map[xpos][ypos] / Constant.pht_coeff) * n;

		return(enr);
	}
	//
	public int bot_count() {
		int count = 0;
		for (int i = 0; i < 8; i++) {
			int[] pos = get_rotate_position(i);
			if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
				IBot o = occupant(pos);
				if (o != null && o.crowds()) {
					count++;
				}
			}
		}
		return(count);
	}
	//
	public int bot_count(int distance) {
		int count = 0;
		for (int i = 0; i < 8; i++) {
			for (int d = 1; d <= 3; d++) { //дистанция 
				int[] pos = get_rotate_position(i,d);
				if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
					IBot o = occupant(pos);
					if (o != null && o.crowds()) {
						count++;
					}
				}
			}
		}
		return(count);
	}
	//
	public void die_with_organics() {//умереть с появлением органики
		die_with_organics(Ledger.DIE_OTHER);
	}
	public void die_with_organics(int cause) {//cause — причина смерти для журнала (Ledger.DIE_*)
		if (killed == 1) {//повторная смерть удваивала органику и выброс газов
			return;
		}
		killed = 1;
		Ledger.die(cause);
		if (queen == 1) {//корона переходит наследнице, иначе вся колония гибнет вместе с королевой
			pass_crown();
		}
		map[xpos][ypos] = null;
		//закон сохранения: в органику уходит тело (energy_for_multiply, потраченное при рождении)
		//и вся оставшаяся энергия клетки
		double body = Constant.energy_for_multiply / 9.0;
		if (energy < 0) {//отрицательная энергия при смерти просто списывается
			Ledger.in_negative += -energy;
		}
		double enr = (Constant.energy_for_multiply + Math.max(energy, 0)) / 9.0;
		energy = 0;
		for (int i = 0; i < 8; i++) {
			int[] pos = get_rotate_position(i);
			if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
				org_map[pos[0]][pos[1]] += enr;
			}
		}
		org_map[xpos][ypos] += enr;
		co2_map[xpos][ypos] += Constant.die_co2_coeff * body;//выброс газа — по массе тела, как раньше
		oxygen_map[xpos][ypos] -= Constant.die_ox_coeff;
		if (oxygen_map[xpos][ypos] < 0) {
			oxygen_map[xpos][ypos] = 0;
		}
		if(org_map[xpos][ypos] > Constant.org_die_level) {
			Ledger.out_org_clamp += org_map[xpos][ypos] - Constant.org_die_level;
			org_map[xpos][ypos] = Constant.org_die_level;
		}
//		if (co2_map[xpos][ypos] > 1) {
//			co2_map[xpos][ypos] = 1;
//		}
		
		delete_chain();
	}
	//
	public int[] get_rotate_position(int rot){
		int[] pos = new int[2];
		pos[0] = Constant.mod((xpos + Constant.movelist[rot][0]), Constant.world_scale[0]);
		pos[1] = Constant.mod((ypos + Constant.movelist[rot][1]), Constant.world_scale[1]);
		return(pos);
	}
	//
	public int[] get_rotate_position(int rot, int distance){
		int[] pos = new int[2];
		pos[0] = Constant.mod((xpos + Constant.movelist[rot][0] * distance), Constant.world_scale[0]);
		pos[1] = Constant.mod((ypos + Constant.movelist[rot][1] * distance), Constant.world_scale[1]);
		return(pos);
	}
	//
	public Color get_color(int draw_type) {
		Color c = new Color(0, 0, 0);
		if (state == 0) {//рисуем бота
			if (draw_type == 0) {//режим отрисовки хищников
				if (c_red == -1 || c_green == -1 || c_blue == -1) {
					c = new Color(128, 128, 128);
				}else {
					c = new Color(c_red, c_green, c_blue);
				}
			}else if (draw_type == 1) {//цвета
				c = color;
			}else if (draw_type == 2) {//энергии
				double g = Constant.border(energy / 1000.0, 1, 0);
				c = Constant.gradient(new Color(255, 255, 0), new Color(255, 0, 0), g);
			}else if (draw_type == 3) {//кланов
				c = clan_color;
			}else if (draw_type == 4) {//возраста
				int max_age = Constant.max_age;
				if(queen == 1) {
					max_age = Constant.max_age * 2;
				}else {
					
				}
				c = Constant.gradient(new Color(0, 0, 255), new Color(255, 255, 0), (age * 1.0) / Constant.max_age);
			}else if (draw_type == 5) {//типа
				if (type == 0) {
					c = new Color(0, 0, 255);
				}else if (type == 1) {
					c = new Color(0, 255, 0);
				}else if (type == 2) {
					c = new Color(255, 0, 0);
				}else if (type == 3) {
					c = new Color(255, 255, 0);
				}else if (type == 4) {
					c = new Color(255, 0, 255);
				}else if (type == 5) {
					c = new Color(0, 255, 255);
				}else if (type == 6) {
					c = new Color(128, 128, 128);
				}else if (type == 7) {
					c = new Color(0, 128, 255);
				}
			}else if (draw_type == 6) {// свяязи
				int count_ch = count_chains();
				if (count_ch == 0) {
					c = new Color(255, 255, 128);
				}else if (count_ch == 1){
					c = new Color(85, 0, 85);
				}else if (count_ch == 2) {
					c = new Color(200, 0, 200);
				}else if (count_ch >= 5) {
					c = new Color(255, 0, 0);
				}else {
					c = new Color(255, 0, 255);
				}
			}else if(draw_type == 9) {// популяции
				c = pop != null ? pop.color : Color.GRAY;
			}else if(draw_type == 8) {// кланы по геному
				c = new Color(kin_color);
			}else if(draw_type == 7) {// королевы
				if(queen == 0) {
					c = new Color(100, 255, 100);
				}else if(queen == 1) {
					c = new Color(0, 50, 250);
				}else {
					double g = Constant.border(energy / 1000.0, 1, 0);
					c = Constant.gradient(new Color(255, 255, 0), new Color(230, 50, 0), g);					
					//c = new Color(250, 250, 0);
				}
			}
		}else if (state == 1){//рисуем семечко
			c = new Color(127, 60, 0);
		}
		return(c);
	}
	//
	public static Color drift_color(Color c, int amount) {//цвет со случайным сдвигом каждого канала на ±amount
		return new Color(
			Constant.border(c.getRed() + rand.nextInt(-amount, amount + 1), 255, 0),
			Constant.border(c.getGreen() + rand.nextInt(-amount, amount + 1), 255, 0),
			Constant.border(c.getBlue() + rand.nextInt(-amount, amount + 1), 255, 0));
	}
	//
	public void go_red() {//краснеть
		if (c_red == -1 && c_green == -1 && c_blue == -1) {
			c_red = 255;
			c_green = 0;
			c_blue = 0;
		}else {
			c_red = Constant.border(c_red + 3, 255, 0);
			c_green = Constant.border(c_green - 3, 255, 0);
			c_blue = Constant.border(c_blue - 3, 255, 0);
		}
	}
	//
	public void go_green() {//зеленеть
		if (c_red == -1 && c_green == -1 && c_blue == -1) {
			c_red = 0;
			c_green = 255;
			c_blue = 0;
		}else {
			c_red = Constant.border(c_red - 3, 255, 0);
			c_green = Constant.border(c_green + 3, 255, 0);
			c_blue = Constant.border(c_blue - 3, 255, 0);
		}
	}
	//
	public void go_blue() {//синеть
		if (c_red == -1 && c_green == -1 && c_blue == -1) {
			c_red = 0;
			c_green = 0;
			c_blue = 255;
		}else {
			c_red = Constant.border(c_red - 3, 255, 0);
			c_green = Constant.border(c_green - 3, 255, 0);
			c_blue = Constant.border(c_blue + 3, 255, 0);
		}
	}
	//
	public void go_yellow() {//желтеть
		if (c_red == -1 && c_green == -1 && c_blue == -1) {
			c_red = 255;
			c_green = 255;
			c_blue = 0;
		}else {
			c_red = Constant.border(c_red + 3, 255, 0);
			c_green = Constant.border(c_green + 3, 255, 0);
			c_blue = Constant.border(c_blue - 3, 255, 0);
		}
	}
	//
	public void update_chain() {
		int c = 1;
		double energy_sum = energy;
		for (int i = 0; i < 8; i++) {
			if (chain[i]) {
				int[] pos = get_rotate_position(i);
				if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
					Bot n = bot_at(pos);
					if (n != null && n.state == 0) {
						energy_sum += n.energy;
						c++;
					}else {
						chain[i] = false;
					}
				}
			}
		}
		if (c != 1) {
			double enr = energy_sum / c;
			energy = enr;
			for (int i = 0; i < 8; i++) {
				if (chain[i]) {
					int[] pos = get_rotate_position(i);
					if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
						bot_at(pos).energy = enr;
					}
				}
			}
		}
	}
	//
	public void delete_chain() {
		for (int g = 0; g < 8; g++) {
			if (chain[g]) {
				int[] pos = get_rotate_position(g);
				if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
					Bot n = bot_at(pos);
					if (n != null && n.state == 0) {
						n.chain[(g + 4) % 8] = false;
					}
				}
			}
		}
	}
	//
	public boolean has_chain(int rot) {//для отрисовки
		return chain[rot];
	}
	//
	public int count_chains() {
		int sum = 0;
		for (int i = 0; i < 8; i++) {
			if (chain[i]){
				sum++;
			}
		}
		return(sum);
	}
}
