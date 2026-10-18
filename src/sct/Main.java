package sct;
import java.awt.BorderLayout;
import java.io.File;

import javax.swing.*;

public class Main{
	public static void main(String[] args) {
		Presets.apply_args(args);//--preset, --set, --seed, --w, --h, --bots, --theme (см. Presets)
		new File("saved worlds").mkdirs();
		//
		Theme.install_look_and_feel();//до создания окна, чтобы все элементы получили тему
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Oxygen");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			WorldBio wb = new WorldBio();
			GraphPanel graphs = new GraphPanel(wb);
			graphs.setVisible(false);//M — показать графики
			JPanel center = new JPanel(new BorderLayout());//графики только под миром: панель слева остается во всю высоту
			center.add(wb, BorderLayout.CENTER);
			center.add(graphs, BorderLayout.SOUTH);
			frame.add(new WorldBioControler(wb, graphs, 210, frame.getHeight()), BorderLayout.WEST);
			frame.add(center, BorderLayout.CENTER);
			frame.setSize(800, 600);
			frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
			frame.setUndecorated(true);
			frame.setVisible(true);
		});
	}
}
