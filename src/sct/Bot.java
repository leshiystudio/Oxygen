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
	ArrayList<Bot> objects;
	static Random rand = new Random();//один на всех ботов: раньше создавался у каждого; зерно задается в Main
	public Bot[][] map;
	//
	private int x;//позиция
	private int y;
	public int xpos;
	public int ypos;
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
	public int killed = 0;//убит ли бот
	public int queen = 0;//0-обычная особь, 1-королева, 2-подчиненная
	public Bot queen_bot;
	public int queen_command = -1;// команда от королевы
	public int queen_marker = 0;
	//
	private double[][] oxygen_map;
	private double[][] co2_map;
	private double[][] org_map;
	public Bot(int new_xpos, int new_ypos, Color new_color, double new_energy, int new_type, double[][] new_oxygen_map, double[][] new_co2_map, double[][] new_org_map, Bot[][] new_map, ArrayList<Bot> new_objects) {
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
	public int Update(ListIterator<Bot> iterator) {
		if (killed == 0) {
			if (state == 0) {//если бот
				if(queen == 0) {
					age -= (int)(oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1;
					energy -= Constant.energy_for_life;
				}else if (queen == 2){
					if(queen_bot == null || queen_bot.killed > 0) {
						die_with_organics();
						return(0);
					}
					age -= (int)((oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1) * 0.9;
					energy -= Constant.energy_for_life * 0.9;
					
				}else {
					age -= (int)((oxygen_map[xpos][ypos] * Constant.age_minus_coeff) + 1) * 0.9;
					//age -= 1;
					energy -= Constant.energy_for_life * 2; // * 0.8;
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
					energy = 1000;
				}else if(energy > 2000) {
					energy = 2000;
				}
				//
				if (age <= 0 || org_map[xpos][ypos] >= Constant.org_die_level || energy <= 0) {//смерть от старости или от органики
					die_with_organics();
					return(0);
				}
			}else if (state == 1){//если семечко
				if (org_map[xpos][ypos] >= Constant.org_die_level) {//умираем от переизбытка органики
					die_with_organics();
					org_map[xpos][ypos] = Math.max(0, org_map[xpos][ypos] - 0.1);
					return(0);
				}
				//
				int res = move(rotate);//двигаемся
				if (res == 0) {//столкновение
					int[] pos = get_rotate_position(rotate);
					if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
						if (map[pos[0]][pos[1]] != null) {
							die_with_organics();//убить семечко
							map[pos[0]][pos[1]].die_with_organics();//убить бота
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
	public void update_commands(ListIterator<Bot> iterator) {//мозг
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
				rotate += commands[(index + 1) % max_mind] % 8;
				rotate %= 8;
				index += 2;
				index %= max_mind;
			}else if (command == 1) {//сменить направление
				rotate = commands[(index + 1) % max_mind] % 8;
				index += 2;
				index %= max_mind;
			}else if (command == 2 || command == 3 || command == 4) {//фотосинтез
				int sector = Constant.sector(ypos);
				//if (sector <= 7 && pht_org_block != 2 && count_co2() >= Constant.pht_co2_coeff) {
				if (pht_org_block != 2 && count_co2() >= Constant.pht_co2_coeff) {
					pht_org_block = 1;
					double pe = photo_energy(sector);//один вызов: photo_energy расходует органику вокруг
					energy += pe;
					go_green();
					oxygen_map[xpos][ypos] += Constant.pht_ox_coeff * pe;
//					if (oxygen_map[xpos][ypos] > 1) {
//						oxygen_map[xpos][ypos] = 1;
//					}
					co2_map[xpos][ypos] -= Constant.pht_co2_coeff;
					if (co2_map[xpos][ypos] < 0) {
						co2_map[xpos][ypos] = 0;
					}
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 5) {//походить относительно
				int count = bot_count() + 1;
				if (count_oxygen() >= Constant.move_ox_coeff * count && count_chains() == 0) {
					oxygen_map[xpos][ypos] -= Constant.move_ox_coeff * count;
					int sens = move(commands[(index + 1) % max_mind] % 8);
					if (sens == 1) {
						energy -= Constant.energy_for_move;
					}
				}
				index += 2;
				index %= max_mind;
				break;
			}else if(command == 6) {//походить абсолютно
				int count = bot_count() + 1;
				if (count_oxygen() >= Constant.move_ox_coeff * count && count_chains() == 0) {
					oxygen_map[xpos][ypos] -= Constant.move_ox_coeff * count;
					if(move(rotate) > 0) {
						energy -= Constant.energy_for_move;
					}
				}
				index += 1;
				index %= max_mind;
				break;
			}else if (command == 7) {//атаковать относительно
				int count = bot_count() + 1;
				if (count_oxygen() >= Constant.attack_ox_coeff * count) {
					oxygen_map[xpos][ypos] -= Constant.attack_ox_coeff * count;
					attack(commands[(index + 1) % max_mind] % 8);
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 8) {//атаковать абсолютно
				int count = bot_count() + 1;
				if (count_oxygen() >= Constant.attack_ox_coeff * count) {
					oxygen_map[xpos][ypos] -= Constant.attack_ox_coeff * count;
					attack(rotate);
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
				give(commands[(index + 1) % max_mind] % 8);
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 13 | command == 14) {//отдать ресурсы абсолютно
				give(rotate);
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
				multiply(commands[(index + 1) % max_mind] % 8, 0, 0, new_type, false, iterator);
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 19) {//поделиться абсолютно
				int new_type = commands[(index + 1) % max_mind] % 8;
				multiply(rotate, 0, 0, new_type, false, iterator);
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
				unif_distrib(commands[(index + 1) % max_mind] % 8);
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 24) {//равномерное распределение ресурсов абсолютно
				unif_distrib(rotate);
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
				if (pht_org_block != 1) {
					pht_org_block = 2;
					recycle_organics_under((int)(commands[(index + 1) % max_mind] * Constant.org_recycle_coeff));
				}
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 29 || command == 30) {//переработка органики перед собой относительно
				if (pht_org_block != 1) {
					pht_org_block = 2;
					recycle_organics(commands[(index + 1) % max_mind] % 8, (int)(commands[(index + 2) % max_mind] * Constant.org_recycle_coeff));
				}
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 31 || command == 32) {//переработка органики перед собой абсолютно
				if (pht_org_block != 1) {
					pht_org_block = 2;
					recycle_organics(rotate, (int)(commands[(index + 1) % max_mind] * Constant.org_recycle_coeff));
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
				rotate = rand.nextInt(8);
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
				multiply(rot, 1, time, new_type, false, iterator);
				index += 4;
				index %= max_mind;
				break;
			}else if (command == 40) {//стрелять семечком абсолютно
				int time = commands[(index + 1) % max_mind] % 16 + 1;
				int new_type = commands[(index + 2) % max_mind] % 8;
				multiply(rotate, 1, time, new_type, false, iterator);
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 41) {//какой у меня тип
				index = commands[(index + 1 + type) % max_mind];
			}else if (command == 42) {//добавление в цепочку относительно
				int new_type = commands[(index + 2) % max_mind] % 8;
				multiply(commands[(index + 1) % max_mind] % 8, 0, 0, new_type, true, iterator);
				index += 3;
				index %= max_mind;
				break;
			}else if (command == 43) {//добавление в цепочку абсолютно
				int new_type = commands[(index + 1) % max_mind] % 8;
				multiply(rotate, 0, 0, new_type, true, iterator);
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
				add_neighbour_to_chain(commands[(index + 1) % max_mind] % 8);
				index += 2;
				index %= max_mind;
				break;
			}else if (command == 46) {//присоеденить соседа к цепочке абсолютно
				add_neighbour_to_chain(rotate);
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
					set_queen(queen_bot,this,1);
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
					set_queen(this,this,0);
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
					queen_orders(distance, do_command, 0);
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
					queen_orders(distance, do_command, 1);
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
					queen_orders(distance, do_command, -1);
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
	//КОМАНДЫ
	//
	public void add_neighbour_to_chain(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
				chain[rot] = true;
				map[pos[0]][pos[1]].chain[(rot + 4) % 8] = true;
			}
		}
	}
	//
	public int move(int rot) {
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (map[pos[0]][pos[1]] == null) {
				Bot self = map[xpos][ypos];
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
			if (map[pos[0]][pos[1]] != null) {
				Bot victim = map[pos[0]][pos[1]];
				if (victim != null) {
					energy += victim.energy;
					victim.energy = 0;//энергию забрал хищник: в органику уходит только тело
					victim.die_with_organics();
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
				Bot n = map[pos[0]][pos[1]];
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
		Bot b = map[pos[0]][pos[1]];
		if (org_map[pos[0]][pos[1]] >= Constant.org_die_level) {
			return(3);//если переизбыток органики
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
			if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
				Bot relative = map[pos[0]][pos[1]];
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
			if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
				Bot relative = map[pos[0]][pos[1]];
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
	public void multiply(int rot, int seed, int time, int new_type, boolean ch, ListIterator<Bot> iterator) {
		int max_mind = Constant.mind_size;
		int[] pos = get_rotate_position(rot);
		if (pos[1] >= 0 && pos[1] < Constant.world_scale[1]) {
			if (map[pos[0]][pos[1]] == null) {
				energy -= Constant.energy_for_multiply;
				if (energy <= 0) {
					die_with_organics();
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
					if (rand.nextInt(100) < Constant.mutate_chance_percent) {//мутация
						new_brain[rand.nextInt(Constant.mind_count)][rand.nextInt(max_mind)] = rand.nextInt(Constant.mind_max_value_command);
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
					new_bot.clan_color = clan_color;
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
					map[pos[0]][pos[1]] = new_bot;
				}
			}
		}
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
						org_map[pos[0]][pos[1]] = Math.max(0, org_map[pos[0]][pos[1]] - 0.0001);
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
				if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
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
					if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
						count++;
					}
				}
			}
		}
		return(count);
	}
	//
	public void die_with_organics() {//умереть с появлением органики
		if (killed == 1) {//повторная смерть удваивала органику и выброс газов
			return;
		}
		killed = 1;
		if (queen == 1) {//корона переходит наследнице, иначе вся колония гибнет вместе с королевой
			pass_crown();
		}
		map[xpos][ypos] = null;
		//закон сохранения: в органику уходит тело (energy_for_multiply, потраченное при рождении)
		//и вся оставшаяся энергия клетки
		double body = Constant.energy_for_multiply / 9.0;
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
					if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
						energy_sum += map[pos[0]][pos[1]].energy;
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
						map[pos[0]][pos[1]].energy = enr;
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
					if (map[pos[0]][pos[1]] != null && map[pos[0]][pos[1]].state == 0) {
						map[pos[0]][pos[1]].chain[(g + 4) % 8] = false;
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
