package gameClient;

import Server.Game_Server;
import Server.game_service;
import algorithms.Graph_Algo;
import dataStructure.DGraph;
import dataStructure.edge_data;
import dataStructure.graph;
import dataStructure.node_data;
import gameClient.Robot;
import oop_dataStructure.oop_edge_data;
import oop_dataStructure.oop_graph;

import org.json.JSONException;
import org.json.JSONObject;
import utils.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class MyGameAuto extends JFrame implements ActionListener, MouseListener {

	public static final double EPS1 = 0.001, EPS2 = Math.pow(EPS1, 2);
	private static final FruitComperator comp = new FruitComperator();
	private graph graph;
	private Graph_Algo algoGraph;
	private ArrayList<Fruit> fruits = new ArrayList<Fruit>();
	private ArrayList<Robot> robots = new ArrayList<Robot>();
	final int NODE_WIDTH_HEIGHT = 10;
	private game_service game;
	final int FRAME_WIDTH = 1000;
	final int FRAME_HEIGHT = 1000;
	static int udst = -1;
	private Image doubleBuffer;
	public static KML_Logger km = null;
	private int dt = 185;

	public MyGameAuto(int num_scenario, int id) throws InterruptedException {
		Game_Server.login(id);
		this.game = Game_Server.getServer(num_scenario);
		graph = new DGraph(game.getGraph());
		algoGraph = new Graph_Algo(graph);
		km = new KML_Logger(num_scenario);
		turnToGuiLocation(FRAME_WIDTH, FRAME_HEIGHT);
		for (String f : game.getFruits()) {
			Fruit ftmp = new Fruit(f);
			placeFruit(ftmp);
			fruits.add(ftmp);
		}
		int size = this.fruits.size();
		this.fruits.sort(comp);
		try {
			JSONObject robots = new JSONObject(game.toString());
			robots = robots.getJSONObject("GameServer");
			int num_robots = robots.getInt("robots");
			int j=39;
			for (int i = 0; i < num_robots; i++) {
				if (i < this.fruits.size())
				{
					int src = this.fruits.get(i).getEdge().getDest();
					node_data node_src = this.graph.getNode(src);
					Point3D src_p = node_src.getLocation();

					game.addRobot(src);
					if(j!=20)
						j=20;
					else
						j=2;
				} 
				else 
				{
					game.addRobot(i);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (String robot : game.getRobots()) {
			Robot robot_tmp = new Robot(robot);
			robots.add(robot_tmp);
		}

		JLabel time = new JLabel();
		JTextField text = new JTextField();
		text.setFont(new Font("deafult", Font.BOLD, 15));
		time.setFont(new Font("deafult", Font.BOLD, 21));
		this.setBounds(200, 0, FRAME_WIDTH, FRAME_HEIGHT);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setTitle("maze of waze");
		this.add(time, BorderLayout.NORTH);
		addMouseListener(this);
		this.setVisible(true);
		game.startGame();
		while (game.isRunning()) {
			time.setText("The clock is ticking: " + game.timeToEnd() / 1000);
			Thread.sleep(dt);
			System.out.println(dt);
			moveRobots(game, graph);
			update(getGraphics());
		}
		km.kmlEnd();
		String results = game.toString();
		String remark = km.kmlToString();
		game.sendKML(remark);
		System.out.println(remark);
		JOptionPane.showMessageDialog(this, "Game over: " + results + "\n game saved to data in kml file",
				"INFORMATION", JOptionPane.INFORMATION_MESSAGE);
	}

	private void turnToGuiLocation(int width, int height) {
		double[] xScale = find_min_max_Xaxis();
		double[] yScale = find_min_max_Yaxis();
		for (node_data node : this.graph.getV()) {
			double x_gui = scale(node.getLocation().x(), xScale[0], xScale[1], 50, width - 50);
			double y_gui = scale(node.getLocation().y(), yScale[0], yScale[1], 70, height - 70);
			node.setGuiLocation(x_gui, y_gui);
		}

	}

	private void placeFruit(Fruit f) {
		for (node_data n : this.graph.getV()) {
			for (edge_data e : this.graph.getE(n.getKey())) {
				node_data dest = this.graph.getNode(e.getDest());
				double nd = n.getLocation().distance3D(f.getLocation());
				double nf = f.getLocation().distance3D(dest.getLocation());
				double dist = n.getLocation().distance3D(dest.getLocation());
				double tmp = dist - (nd + nf);
				int type;
				if (n.getKey() > dest.getKey()) {
					type = 1; // apple
				} else {
					type = -1; // banana
				}

				if ((Math.abs(tmp) <= EPS2) && (f.getType() == type)) {
					f.setEdge(e);
					return;
				}
			}
		}
	}

	private void moveRobots(game_service game, graph graph) {
		List<String> path = game.move();
		List<String> robots = game.getRobots();
		int current = -1;
		if (path != null) {
			for (int i = 0; i < path.size(); i++) {
				String robot_json = path.get(i);
				try {
					JSONObject line = new JSONObject(robot_json);
					JSONObject rob = line.getJSONObject("Robot");
					int robid = rob.getInt("id");
					int src = rob.getInt("src");
					int dest = rob.getInt("dest");
					String pos = rob.getString("pos");
					// not optimal and has bugs
					if (current != -1
							&& this.robots.get(robid).getLocation().close2equals(this.robots.get(current).getLocation())
							&& dest == -1) {

						dest = nextNodeR(graph, src);
						game.chooseNextEdge(robid, dest);
						// src = src + 1;
					} else if (dest == -1) {
						current = robid;
						dest = nextNode(graph, src);
						game.chooseNextEdge(robid, dest);
					}
					for (Fruit f : fruits) {
						if (this.robots.get(robid).getLocation()
								.close2equals(graph.getNode(f.getEdge().getDest()).getLocation())
								|| this.robots.get(robid).getLocation().close2equals(f.getLocation())
								|| this.robots.get(robid).getLocation()
								.close2equals(graph.getNode(f.getEdge().getSrc()).getLocation()))
							dt = 100;//We need to play with this variable in order to level up the different stages
					}

					this.robots.get(robid).setLocation(new Point3D(pos));
					km.addPlaceMark("robot", pos);

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			fruits.clear();
			for (String fruit : game.getFruits()) {
				Fruit fruit_tmp = new Fruit(fruit);
				placeFruit(fruit_tmp);
				fruits.add(fruit_tmp);

			}
			fruits.sort(comp);
		}
	}

	private int nextNode(graph graph, int src) {
		synchronized (algoGraph) {
			dt = 280;//Change it through the levels
			Double min = Double.MAX_VALUE;
			int dest = 0;
			int key=0;
			boolean b = false;
			//Sometimes fruits.sort is used
			// fruits.sort(comp.reversed());
			Fruit f = new Fruit();
			// placeFruit(f);
			for (int i = 0; i < game.getFruits().size(); i++) {
				f = fruits.get(game.getFruits().size() - i - 1);
				placeFruit(f);
				if (f.getEdge().getTag() == 0 && algoGraph.shortestPathDist(src, f.getEdge().getSrc()) < min) 
				{
					key=i;
					b = true;
					min = algoGraph.shortestPathDist(src, f.getEdge().getDest());
					if (src == f.getEdge().getSrc()) {
						dest = f.getEdge().getDest();
					} else {
						dest = f.getEdge().getSrc();
					}

				}
			}
			if (min == -1)
				return -1;
			if (!b) {
				// fruits.sort(comp.reversed());
				// f = new Fruit(fruits.get(0));
				placeFruit(f);
				if (src == f.getEdge().getSrc()) {
					dest = f.getEdge().getDest();
				} else
					dest = f.getEdge().getSrc();
			}
			// fruits.sort(comp.reversed());
			//			f = new Fruit(fruits.get(key));
			//			placeFruit(f);
			// f.getEdge().setTag(1);
			graph.getEdge(f.getEdge().getDest(), f.getEdge().getSrc()).setTag(1);
			List<node_data> node = algoGraph.shortestPath(src, dest);
			Collection<edge_data> c = graph.getE(src);
			if (node.size() <= 1)
				return -1;
			if (src == node.get(1).getKey()) {
				if (f.getType() == -1) {
					return src + 1;
				}
				return src - 1;
			}
			return node.get(1).getKey();
		}

	}

	private static int nextNodeR(graph g, int src) {
		int ans = -1;
		Collection<edge_data> ee = g.getE(src);
		Iterator<edge_data> itr = ee.iterator();
		int s = ee.size();
		int r = (int) (Math.random() * s);
		int i = 0;
		while (i < r) {
			itr.next();
			i++;
		}
		ans = itr.next().getDest();
		return ans;
	}

	public void update(Graphics g) {
		Dimension size = getSize();
		if (doubleBuffer == null || doubleBuffer.getWidth(this) != size.width
				|| doubleBuffer.getHeight(this) != size.height) {
			doubleBuffer = createImage(size.width, size.height);
		}
		if (doubleBuffer != null) {
			// paint to double buffer
			Graphics g2 = doubleBuffer.getGraphics();
			// g2.setColor(getBackground());
			paint(g2);
			g2.dispose();
			// copy double buffer to screen
			g.drawImage(doubleBuffer, 0, 0, null);
		} else {
			// couldn't create double buffer, just paint to screen
			paint(g);
		}
	}

	public void paintComponents(Graphics g) {
		super.paintComponents(g);
	}

	private void drawNodes(Graphics2D g) {
		double[] x_toScale = find_min_max_Xaxis();
		double[] y_toScale = find_min_max_Yaxis();
		ArrayList<String> locations = new ArrayList<String>();
		for (node_data node : graph.getV()) {
			double x_gui = scale(node.getLocation().x(), x_toScale[0], x_toScale[1], 50, this.getWidth() - 50);
			double y_gui = scale(node.getLocation().y(), y_toScale[0], y_toScale[1], 70, this.getHeight() - 70);
			y_gui = this.getHeight() - y_gui;
			g.setColor(Color.MAGENTA);
			g.drawOval((int) x_gui - 3, ((int) y_gui) - 3, 20, 20);
			String id = node.getKey() + "";
			g.setFont(new Font("default", Font.BOLD, 14));
			g.setColor(Color.BLACK);
			g.drawString(id, (int) x_gui + 7, ((int) y_gui) + 15);
			if (node.getLocation().toString() != null) {
				km.addPlaceMark("node", node.getLocation().toString());
				locations.add(node.getLocation().toString());
			}
		}
		km.addLineString(locations);
	}

	private void drawFruits(Graphics2D g) {
		double[] x_toScale = find_min_max_Xaxis();
		double[] y_toScale = find_min_max_Yaxis();

		for (Fruit fruit : this.fruits) {

			if (fruit.getType() == 1) {
				/**
				 * Here put icon of an apple
				 */
				km.addPlaceMark("fruit-apple", fruit.getLocation().toString());
				g.setColor(Color.RED);
			} else {
				/**
				 * Here put icon of a banana
				 */
				km.addPlaceMark("fruit-banana", fruit.getLocation().toString());
				g.setColor(Color.YELLOW);
			}
			double x_gui = scale(fruit.getLocation().x(), x_toScale[0], x_toScale[1], 50, this.getWidth() - 50);
			double y_gui = scale(fruit.getLocation().y(), y_toScale[0], y_toScale[1], 70, this.getHeight() - 70);
			y_gui = this.getHeight() - y_gui;
			fruit.setLocationOnGui(x_gui, y_gui);
			Shape circle = new Arc2D.Double(fruit.getLocationOnGui().x() - 10, fruit.getLocationOnGui().y() - 10, 30,
					30, 0, 360, Arc2D.CHORD);
			g.fill(circle);
			String id = fruit.getValue() + "";
			g.setFont(new Font("deafult", Font.BOLD, 14));
			g.setColor(Color.BLUE);
			g.drawString(id, fruit.getLocationOnGui().ix() + 1, fruit.getLocationOnGui().iy() + 11);
		}

	}

	private void drawEdges(Graphics2D g) {
		double[] x_toScale = find_min_max_Xaxis();
		double[] y_toScale = find_min_max_Yaxis();
		for (node_data node : graph.getV()) {
			if (graph.getE(node.getKey()) != null) {
				for (edge_data edge : graph.getE(node.getKey())) {
					double x_gui = scale(node.getLocation().x(), x_toScale[0], x_toScale[1], 50, this.getWidth() - 50);
					double y_gui = scale(node.getLocation().y(), y_toScale[0], y_toScale[1], 70, this.getHeight() - 70);
					y_gui = this.getHeight() - y_gui;
					node_data dst = graph.getNode(edge.getDest());
					double x_guir = scale(dst.getLocation().x(), x_toScale[0], x_toScale[1], 50, this.getWidth() - 50);
					double y_guir = scale(dst.getLocation().y(), y_toScale[0], y_toScale[1], 70, this.getHeight() - 70);
					y_guir = this.getHeight() - y_guir;
					g.setColor(Color.BLACK);
					g.setFont(new Font("deafult", Font.BOLD, 14));
					String weight = edge.getWeight() + "";
					g.drawLine((int) x_gui, (int) y_gui, (int) x_guir, (int) y_guir);
					turnToGuiLocation(1000, 1000);
					g.setColor(Color.GREEN);

					// calculate the direction oval location
					int mid_x = (((int) x_gui + (int) x_guir) / 2);
					int mid_y = (((int) y_gui + (int) y_guir) / 2);
					int d_x = (((((mid_x + (int) x_guir) / 2) + (int) x_guir) / 2) + (int) x_guir) / 2;
					int d_y = (((((mid_y + (int) y_guir) / 2) + (int) y_guir) / 2) + (int) y_guir) / 2;

					g.fillOval(d_x - 3, d_y - 3, 5, 5);
				}
			}
		}
	}

	private void drawRobots(Graphics2D g) {
		double[] x_toScale = find_min_max_Xaxis();
		double[] y_toScale = find_min_max_Yaxis();
		for (Robot robot : this.robots) {
			g.setColor(Color.BLACK);
			g.setStroke(new BasicStroke((float) 3.0));
			double x_gui = scale(robot.getLocation().x(), x_toScale[0], x_toScale[1], 50, this.getWidth() - 50);
			double y_gui = scale(robot.getLocation().y(), y_toScale[0], y_toScale[1], 70, this.getHeight() - 70);
			y_gui = this.getHeight() - y_gui;
			robot.setGui_location(x_gui, y_gui);
			g.fillOval((int) robot.getGui_location().x() - 7, (int) robot.getGui_location().y() - 7, 25, 25);
		}
	}

	public void paint(Graphics graphics) {
		super.paint(graphics);
		BufferedImage buffer = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = buffer.createGraphics();
		super.paint(g2d);
		Graphics2D g2dComponent = (Graphics2D) graphics;
		g2dComponent.drawImage(buffer, null, 0, 0);
		Graphics2D g = (Graphics2D) graphics;

		/**
		 * Functions of drawing...
		 */
		drawNodes(g);
		drawFruits(g);
		drawEdges(g);
		drawRobots(g);

	}

	private double[] find_min_max_Xaxis() {
		double[] x_scale = new double[2];
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;

		for (node_data node : this.graph.getV()) {

			if (node.getLocation().x() < min)
				min = node.getLocation().x();
			else {
				if (node.getLocation().x() > max) {
					max = node.getLocation().x();
				}
			}
		}

		x_scale[0] = min;
		x_scale[1] = max;

		return x_scale;

	}

	private double[] find_min_max_Yaxis() {
		double[] y_scale = new double[2];
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;

		for (node_data node : this.graph.getV()) {

			if (node.getLocation().y() < min)
				min = node.getLocation().y();
			else {
				if (node.getLocation().y() > max) {
					max = node.getLocation().y();
				}
			}
		}

		y_scale[0] = min;
		y_scale[1] = max;

		return y_scale;

	}

	private double scale(double data, double r_min, double r_max, double t_min, double t_max) {
		double res = ((data - r_min) / (r_max - r_min)) * (t_max - t_min) + t_min;
		return res;
	}

	@Override
	public void actionPerformed(ActionEvent e) {

	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// game.stopGame();

	}

	@Override
	public void mousePressed(MouseEvent e) {

	}

	@Override
	public void mouseReleased(MouseEvent e) {

	}

	@Override
	public void mouseEntered(MouseEvent e) {

	}

	@Override
	public void mouseExited(MouseEvent e) {

	}

	public static void main(String[] a) throws InterruptedException {

		//		game_service game = Game_Server.getServer(24); // you have [0,23]
		// games
		// System.out.println(game.getGraph());
		MyGameAuto m = new MyGameAuto(4, 315524389);

		// System.out.println(m.g);
	}

}