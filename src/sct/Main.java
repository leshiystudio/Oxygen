package sct;
import java.awt.BorderLayout;
import java.io.File;

import javax.swing.*;

public class Main{
	public static void main(String[] args) {
		new File("saved worlds").mkdirs();
		//
		Theme.install_look_and_feel();//до создания окна, чтобы все элементы получили тему
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Oxygen");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			WorldBio wb = new WorldBio();
			frame.add(new WorldBioControler(wb, 200, frame.getHeight()), BorderLayout.WEST);
			frame.add(wb);
			frame.setSize(800, 600);
			frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
			frame.setUndecorated(true);
			frame.setVisible(true);
		});
	}
}
