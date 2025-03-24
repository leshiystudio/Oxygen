package sct;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;

import javax.swing.*;

public class Main{
	public static void main(String[] args) {
		new File("record/predators-oxygen").mkdirs();
		new File("record/energy").mkdirs();
		new File("record/color").mkdirs();
		new File("record/predators-org").mkdirs();
		//new File("record/predators-co2").mkdirs();
		//
		new File("saved worlds").mkdirs();
		//
		JFrame frame = new JFrame("Oxygen");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		WorldBio wb = new WorldBio();
		frame.add(new WorldBioControler(wb, 200, frame.getHeight()), BorderLayout.WEST);
		frame.add(wb);
		frame.setSize(800, 600);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setUndecorated(true);
		frame.setVisible(true);
	}
}