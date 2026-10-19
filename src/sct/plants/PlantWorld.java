package sct.plants;

import java.util.List;
import java.util.Random;

import sct.IBot;

//То, что растениям нужно от мира: общая сетка, общая органика, слой минералов (почва), список мира,
//настройки и генератор случайных чисел. Растения хранят ссылку на этот объект, как бот — на карты.
public class PlantWorld {
	public static Random rand = new Random();//свой генератор растений; зерно — Presets.apply_seed
	//
	public final IBot[][] grid;//общая сетка мира
	public final double[][] org;//общая органика (у biosim — SOIL[:, 1])
	public final double[][] soil;//энергия почвы (у biosim — SOIL[:, 0]) = слой минералов мира
	public final double[][] co2;//газы мира Oxygen — для действия absorb_co2; null — газов нет, действие не удается
	public final double[][] oxygen;
	public final List<IBot> objects;//список мира — для рождения вне хода (инструменты, тесты)
	public final PlantConfig c;
	public sct.Population pop;//популяция этих растений (свои настройки и действия)
	public final int W, H;

	public PlantWorld(IBot[][] grid, double[][] org, double[][] soil, List<IBot> objects, PlantConfig c) {
		this(grid, org, soil, null, null, objects, c);
	}
	public PlantWorld(IBot[][] grid, double[][] org, double[][] soil, double[][] co2, double[][] oxygen, List<IBot> objects, PlantConfig c) {
		this.co2 = co2;
		this.oxygen = oxygen;
		this.grid = grid;
		this.org = org;
		this.soil = soil;
		this.objects = objects;
		this.c = c;
		this.W = grid.length;
		this.H = grid[0].length;
	}
}
