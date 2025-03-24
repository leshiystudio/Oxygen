package sct;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class WorldBioControler extends JPanel{
	
	protected WorldBio world;
	protected int width;
	protected int height;
	
	private JButton button_start_stop = new JButton("Stop");
	private JButton button_render_border = new JButton("Render Border: Off");
	private JLabel label_bot_count = new JLabel("label_bot_count: ");
	
	public WorldBioControler(WorldBio world, int width, int height) {
		this.world = world;
		this.width = width;
		this.height = height;
		int padding = 5;
		int x = padding; 
		int w = width - padding*2;
		int h = 30;

        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(width, height));
        //setLayout(new FlowLayout());
        setLayout(null);
        
		button_start_stop.addActionListener(new start_stop());
		button_start_stop.setBounds(x, (h+padding)*0+padding, w, h);
        add(button_start_stop);
        
        JButton new_population_button = new JButton("New population");
        new_population_button.addActionListener(new nwp());
        new_population_button.setBounds(x, (h+padding)*1+padding, w, h);
        add(new_population_button);

        button_render_border.addActionListener(new render_border());
		button_render_border.setBounds(x, (h+padding)*2+padding, w, h);
        add(button_render_border);

        label_bot_count.setBounds(x, (h+padding)*4+padding, w, h);
        add(label_bot_count);
        
        //
        //смена режимов отрисовки
        //
        JButton predators_button = new JButton("Predators");
        predators_button.addActionListener(new change_draw_type(0));
		predators_button.setBounds(x, (h+padding)*10+padding, w, h);
        add(predators_button);
        //
        JButton energy_button = new JButton("Energy");
        energy_button.addActionListener(new change_draw_type(2));
		energy_button.setBounds(x, (h+padding)*11+padding, w, h);
        add(energy_button);
        //
        JButton clan_button = new JButton("Clans");
		clan_button.addActionListener(new change_draw_type(3));
		clan_button.setBounds(x, (h+padding)*12+padding, w, h);
        add(clan_button);
        //
        JButton age_button = new JButton("Age");
        age_button.addActionListener(new change_draw_type(4));
		age_button.setBounds(x, (h+padding)*13+padding, w, h);
        add(age_button);
        //
        JButton color_button = new JButton("Color");
        color_button.addActionListener(new change_draw_type(1));
		color_button.setBounds(x, (h+padding)*14+padding, w, h);
        add(color_button);
        //
        JButton type_button = new JButton("Type");
        type_button.addActionListener(new change_draw_type(5));
        type_button.setBounds(x, (h+padding)*15+padding, w, h);
        add(type_button);
        //
        JButton chain_button = new JButton("Chain");
        chain_button.addActionListener(new change_draw_type(6));
        chain_button.setBounds(x, (h+padding)*16+padding, w, h);
        add(chain_button);
        
        //
        //смена режимов отрисовки фона
        //
        JButton none_button = new JButton("None");
        none_button.addActionListener(new change_res_draw_type(0));
        none_button.setBounds(x, (h+padding)*18+padding, w, h);
        add(none_button);
        //
        JButton ox_button = new JButton("Oxygen");
        ox_button.addActionListener(new change_res_draw_type(1));
        ox_button.setBounds(x, (h+padding)*19+padding, w, h);
        add(ox_button);
        //
        JButton org_button = new JButton("Organics");
        org_button.addActionListener(new change_res_draw_type(2));
        org_button.setBounds(x, (h+padding)*20+padding, w, h);
        add(org_button);
        //
        JButton mnr_button = new JButton("Minerals");
        mnr_button.addActionListener(new change_res_draw_type(3));
        mnr_button.setBounds(x, (h+padding)*21+padding, w, h);
        add(mnr_button);
        //
        JButton co2_button = new JButton("Co2");
        co2_button.addActionListener(new change_res_draw_type(4));
        co2_button.setBounds(x, (h+padding)*22+padding, w, h);
        add(co2_button);
        
	}

	public void paintComponent(Graphics canvas) {
		super.paintComponent(canvas);
		
		label_bot_count.setText("bot_count: "+world.b_count);

        canvas.drawString("steps: " + world.steps, 5, 35*4+5);

        canvas.drawString("render bot type: " + Constant.draw_type_names[world.draw_type] + " view", 5, 35*5+5);  

        canvas.drawString("render res type: " + Constant.res_type_names[world.res_draw_type] + " view", 5, 35*6+5);  

        repaint();
	}
	
	private class start_stop implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.pause = !world.pause;
			if (world.pause) {
				button_start_stop.setText("Start");
			}else {
				button_start_stop.setText("Stop");
			}
		}
	}
	private class render_border implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.render_border = !world.render_border;
			if (world.render_border) {
				button_render_border.setText("Render Border: On");
			}else {
				button_render_border.setText("Render Border: Off");
			}
		}
	}
	private class nwp implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			world.newPopulation();
		}
	}
	private class b_count implements ActionListener{
		public void actionPerformed(ActionEvent e) {
			System.out.println("b_count: "+world.b_count);
			repaint();
		}
	}

	//
	private class change_draw_type implements ActionListener{//смена режима отрисовки(берется из параметра)
		int number;
		private change_draw_type(int new_number){
			number = new_number;
		}
		public void actionPerformed(ActionEvent e) {
			world.draw_type = number;
		}
	}
	private class change_res_draw_type implements ActionListener{//смена режима отрисовки фона(берется из параметра)
		int number;
		private change_res_draw_type(int new_number){
			number = new_number;
		}
		public void actionPerformed(ActionEvent e) {
			world.res_draw_type = number;
		}
	}	
}
