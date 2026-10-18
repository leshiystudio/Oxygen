package sct;

import java.awt.Color;

public class Constant {
	public static int W = 1920;
	public static int H = 1080;
	public static int size = 5;
	public static int[] world_scale = {400,400};//{16 * 50, 9 * 50};
	public static int starting_bot_count = 5000;
	public static int starting_plant_count = 0;//ростков растений (sct.plants) при создании мира; 0 — растений нет
	public static int mind_size = 64; //длина одного генома
	public static int mind_count = 2; //32; количество геномов
	public static int mind_max_value_command = 64; //64; //максимальное значение для индекса команды
	public static double starting_ox = 0.04; //0.02;
	public static double starting_co2 = 0.04; //0.02;
	public static int starting_org = 200;
	public static double ox_render_maximum_coeff = 0.25; //0.25;
	public static double co2_render_maximum_coeff = 0.25; //0.25;
	public static String[] draw_type_names = {"predators", "color", "energy", "clans", "age", "types", "chains", "queens", "kin"}; //индекс = номер режима в Bot.get_color
	public static String[] res_type_names = {"none", "oxygen", "org+oxygen", "minerals", "co2", "height", "soil"};
	public static String[] mouse_func_names = {"select", "set", "remove"};
	public static int[] zoom_sizes = {5, 5, 10}; //{2, 5, 10};
	//
	public static double org_recycle_ox_coeff = 0.000097; //0.000027;
	public static double life_ox_coeff = 0.001; // 0.002;
	public static double move_ox_coeff = 0.003;
	public static double attack_ox_coeff = 0.005; //0.005 //0.01;
	public static double pht_ox_coeff = 0.003; //0.003;
	public static double die_ox_coeff = 0.01;
	public static double evaporation_ox_coeff = 0.9994; //0.98;
	public static double ox_distribution_min = 0.001; //0.001;
	//
	public static double life_co2_coeff = 0.001;
	public static double pht_co2_coeff = 0.0003;
	public static double org_recycle_co2_coeff = 0.000097; //0.0001;
	public static double die_co2_coeff = 0.0003;
	//
	public static double org_die_level = 800.0;
	public static int age_minus_coeff = 20;
	public static int energy_for_life = 1;
	public static int energy_for_move = 1;
	public static int energy_for_multiply = 50;
	public static int energy_for_auto_multiply = 800;
	public static int max_age = 1500;
	public static int max_different_genom = 10; //3; порог "свой/чужой" по всем генам всех геномов (сейчас 2*64)
	public static int mutate_chance_percent = 10;
	//
	public static int queen_max_radius = 8;
	//
	public static String bot_actions_off = "";//выключенные действия бота через запятую (BotAction), например "attack,seed"
	public static String bot_brain = "genome";//мозг ботов (Brains): genome — геном команд
	public static String plant_actions_off = "";//выключенные действия растений (PlantAction), например "eat,move"
	//
	public static double pht_coeff = 110; //400;
	public static int[] pht_energy_list = {12, 11, 10, 9, 8, 7, 6, 5};
	public static double pht_neighbours_coeff = 0.1111*2;
	public static double org_recycle_coeff = 2; //1;
	//
	public static int[][] movelist = {
		{0, -1},
		{1, -1},
		{1, 0},
		{1, 1},
		{0, 1},
		{-1, 1},
		{-1, 0},
		{-1, -1}
	};
	//
	public static int border(int number, int border1, int border2) {
		if (number > border1) {
			number = border1;
		}else if (number < border2) {
			number = border2;
		}
		return(number);
	}
	public static double border(double number, double border1, double border2) {
		if (number > border1) {
			number = border1;
		}else if (number < border2) {
			number = border2;
		}
		return(number);
	}
	public static int sector(int y) {
		int sec = y / (Constant.world_scale[1] / 8);
		if (sec > 7) {
			sec = 7;
		}
		return(sec);
	}
	public static int[] get_rotate_position(int rot, int[] sp){
		int[] pos = new int[2];
		pos[0] = Constant.mod((sp[0] + Constant.movelist[rot][0]), Constant.world_scale[0]);
		pos[1] = Constant.mod((sp[1] + Constant.movelist[rot][1]), Constant.world_scale[1]);
		
//		int[] pos = new int[2];
//		pos[0] = (sp[0] + Constant.movelist[rot][0]) % Constant.world_scale[0];
//		pos[1] = sp[1] + Constant.movelist[rot][1];
//		if (pos[0] < 0) {
//			pos[0] = Constant.world_scale[0] - 1;
//		}else if(pos[0] >= Constant.world_scale[0]) {
//			pos[0] = 0;
//		}
		return(pos);
	}
	public static Color gradient(Color color1, Color color2, double grad) {
		grad = Constant.border(grad, 1.0, 0.0);//за пределами 0..1 цвет по модулю 256 перескакивал на противоположный
		int r = (int)(color1.getRed() * (1 - grad) + color2.getRed() * grad);
		int g = (int)(color1.getGreen() * (1 - grad) + color2.getGreen() * grad);
		int b = (int)(color1.getBlue() * (1 - grad) + color2.getBlue() * grad);
		int a = (int)(color1.getAlpha() * (1 - grad) + color2.getAlpha() * grad);
		return(new Color(r, g, b, a));
	}
	public static int mod(int a, int b) {
		return (a % b + b) % b;
	}
    public static double normalize(double min, double max, double value) {
        return (value - min) / (max - min);
    }
}
