package edu.illinois.mitra.starlSim.main;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import edu.illinois.mitra.starl.harness.IdealSimGpsProvider;
import edu.illinois.mitra.starl.harness.RealisticSimGpsProvider;
import edu.illinois.mitra.starl.harness.SimGpsProvider;
import edu.illinois.mitra.starl.harness.SimulationEngine;
import edu.illinois.mitra.starl.interfaces.LogicThread;
import edu.illinois.mitra.starl.objects.ItemPosition;
import edu.illinois.mitra.starl.objects.ObstacleList;
import edu.illinois.mitra.starl.objects.PositionList;
import edu.illinois.mitra.starlSim.draw.DrawFrame;
import edu.illinois.mitra.starlSim.draw.RobotData;


public class Simulation {
	private Collection<SimApp> bots = new HashSet<SimApp>();
	private HashMap<String, String> participants = new HashMap<String, String>();
	private SimGpsProvider gps;
	private SimulationEngine simEngine;

	private ExecutorService executor;

	private final SimSettings settings;
	
	private final DrawFrame drawFrame;
	private ObstacleList list; 
	
	public Simulation(Class<? extends LogicThread> app, final SimSettings settings) {
		if(settings.N_BOTS <= 0)
			throw new IllegalArgumentException("Must have more than zero robots to simulate!");

		// Create set of robots whose wireless is blocked for passage between
		// the GUI and the simulation communication object
		Set<String> blockedRobots = new HashSet<String>();
		
		// Create participants and instantiate SimApps
		for(int i = 0; i < settings.N_BOTS; i++) {
			// Mapping between robot name and IP address
			participants.put(settings.BOT_NAME + i, "192.168.0." + i); // TODO: this will break if i >= 255, can use other subnets
		}

		// Initialize viewer
		drawFrame = new DrawFrame(participants.keySet(), blockedRobots, settings);
		drawFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// Start the simulation engine
		LinkedList<LogicThread> logicThreads = new LinkedList<LogicThread>();
		simEngine = new SimulationEngine(settings.SIM_TIMEOUT, settings.MSG_MEAN_DELAY, settings.MSG_STDDEV_DELAY, settings.MSG_LOSSES_PER_HUNDRED, settings.MSG_RANDOM_SEED, settings.TIC_TIME_RATE, blockedRobots, participants, drawFrame.getPanel(), logicThreads);

		// Create the sim gps
		if(settings.IDEAL_MOTION) {
			gps = new IdealSimGpsProvider(simEngine, settings.GPS_PERIOD, settings.GPS_ANGLE_NOISE, settings.GPS_POSITION_NOISE);
		} else {
			gps = new RealisticSimGpsProvider(simEngine, settings.GPS_PERIOD, settings.GPS_ANGLE_NOISE, settings.GPS_POSITION_NOISE, settings.BOT_RADIUS);
		}

		// Load waypoints
		if(settings.WAYPOINT_FILE != null)
			gps.setWaypoints(WptLoader.loadWaypoints(settings.WAYPOINT_FILE));
		
		// Load sensepoints
		if(settings.SENSEPOINT_FILE != null)
			gps.setSensepoints(SptLoader.loadSensepoints(settings.SENSEPOINT_FILE));
		
		// Load Obstacles
		if(settings.OBSPOINT_FILE != null)
		{			
			gps.setObspoints(ObstLoader.loadObspoints(settings.OBSPOINT_FILE));
			list = gps.getObspointPositions();
			list.detect_Precision = settings.Detect_Precision;
			list.de_Radius = settings.De_Radius;
			//should we grid the environment?
			if(settings.Detect_Precision > 1){
				list.Gridfy();
			}
			gps.setViews(list, settings.N_BOTS);
		}
		else{
			//if we have no input files, we still have to initialize the obstacle list so that later on, if we detect collision between robots, we can add that obstacle
			gps.setObspoints(new ObstacleList());
			list = gps.getObspointPositions();
			list.detect_Precision = settings.Detect_Precision;
			list.de_Radius = settings.De_Radius;
			gps.setViews(list, settings.N_BOTS);
		}
		
		
		this.settings = settings;
		simEngine.setGps(gps);
		gps.start();

		// Load initial positions
		PositionList initialPositions;
		if(settings.INITIAL_POSITIONS_FILE != null){
			initialPositions = WptLoader.loadWaypoints(settings.INITIAL_POSITIONS_FILE);
		}
		else
			initialPositions = new PositionList();

		Random rand = new Random();

		// Create each robot
		for(int i = 0; i < settings.N_BOTS; i++) {
			String botName = settings.BOT_NAME + i;

			ItemPosition initialPosition = initialPositions.getPosition(botName);
			// If no initial position was supplied, randomly generate one
			if(initialPosition == null) {	
			//	System.out.println("null position in list");
				int retries = 0;
				boolean valid = false;
				
				while(retries++ < 10000 && (!acceptableStart(initialPosition) || !valid))
				{
                    // TODO: scale the grid size or valid initial positions by N
					initialPosition = new ItemPosition(botName, rand.nextInt(settings.GRID_XSIZE), rand.nextInt(settings.GRID_YSIZE), rand.nextInt(360));
					if(list != null){
						valid = (list.validstarts(initialPosition, settings.BOT_RADIUS));
					}	
				}
				if(retries > 10000)
				{
					System.out.print("too many tries for BOT"+botName+"\n");
				}
			}
			if(i< settings.N_GBOTS){
				initialPosition.type = 0;
			}
			if((i>=settings.N_GBOTS) && (i<(settings.N_GBOTS + settings.N_DBOTS))){
				initialPosition.type = 1;	
			}
			if((i>=(settings.N_GBOTS + settings.N_DBOTS)) && (i<(settings.N_GBOTS + settings.N_DBOTS + settings.N_RBOTS))){
				initialPosition.type = 2;
			}
			
			initialPosition.radius = settings.BOT_RADIUS;
			
			SimApp sa = new SimApp(botName, participants, simEngine, initialPosition, settings.TRACE_OUT_DIR, app, drawFrame, settings.TRACE_CLOCK_DRIFT_MAX, settings.TRACE_CLOCK_SKEW_MAX);

			bots.add(sa);

			logicThreads.add(sa.logic);
		}

		// initialize debug drawer class if it was set in the settings
		if(settings.DRAWER != null)
			drawFrame.addPredrawer(settings.DRAWER);

		// GUI observer updates the viewer when new positions are calculated
		Observer guiObserver = new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				ArrayList<ItemPosition> pos = ((PositionList) arg).getList();
				ArrayList<RobotData> rd = new ArrayList<RobotData>();
				Vector<ObstacleList> views = gps.getViews();
				//define robot colors
				int i = 0;
				Color[] c = new Color[12] ; // TODO: use a counter and not magic numbers
				c[0] = Color.BLACK;
				c[1] = Color.BLUE;
				c[2] = Color.GREEN;
				c[3] = Color.MAGENTA;
				c[4] = Color.ORANGE;
				c[5] = Color.CYAN;
				c[6] = Color.GRAY;
				c[7] = Color.PINK;
				c[8] = Color.RED;
				c[9] = Color.LIGHT_GRAY;
				c[10] = Color.YELLOW;
				c[11] = Color.DARK_GRAY;
			
				
				// Add robots and views
				for(ItemPosition ip : pos) {	
					if(i<12){ // TODO: avoid magic numbers, use mod
						RobotData nextBot = new RobotData(ip.name, ip.x, ip.y, ip.angle, c[i], views.elementAt(i), ip.leftbump, ip.rightbump);
						nextBot.radius = settings.BOT_RADIUS;
						nextBot.type = ip.type;
						rd.add(nextBot);
						i++;
					}
					else{
						RobotData nextBot = new RobotData(ip.name, ip.x, ip.y, ip.angle, c[0], views.elementAt(i), ip.leftbump, ip.rightbump);
						nextBot.radius = settings.BOT_RADIUS;
						rd.add(nextBot);
					}
						
				}
				// Add waypoints
				if(settings.DRAW_WAYPOINTS) {
					for(ItemPosition ip : gps.getWaypointPositions().getList()) {
						RobotData waypoint = new RobotData((settings.DRAW_WAYPOINT_NAMES ? ip.name : ""), ip.x, ip.y, ip.angle);
						waypoint.radius = 5;
						waypoint.c = new Color(255, 0, 0);
						rd.add(waypoint);
					}
				}
                drawFrame.updateCollisions(gps.getRobotCollisions(), gps.getObstacleCollisions());

				drawFrame.updateData(rd, simEngine.getTime());
				//add obstacle update later
			}
		};
		gps.addObserver(guiObserver);
		
		

		if(settings.USE_GLOBAL_LOGGER)
			gps.addObserver(createGlobalLogger(settings));


		// show viewer
		drawFrame.setVisible(true);
	}

	private static final double BOT_SPACING_FACTOR = 2.8;
	private Map<String, ItemPosition> startingPositions = new HashMap<String, ItemPosition>();

    /**
     * Check if initial positions are too close together
     * @param pos
     * @return
     */
	private boolean acceptableStart(ItemPosition pos) {
		if(pos == null)
			return false;
		startingPositions.put(pos.getName(), pos);
		for(Entry<String, ItemPosition> entry : startingPositions.entrySet()) {
			if(!entry.getKey().equals(pos.getName())) {
				if(entry.getValue().distanceTo(pos) < (BOT_SPACING_FACTOR * settings.BOT_RADIUS)) { // TODO: sort based on distance to improve performance for large N (as this may be called thousands of times when starting simulation)
                    return false;
                }
			}
		}
		return true;
	}

	/**
	 * Add an Observer to the list of GPS observers. This Observer's update
	 * method will be passed a PositionList object as the argument. This must be
	 * called before the simulation is started!
	 * 
	 * @param o
	 */
	public void addPositionObserver(Observer o) {
		if(executor == null)
			gps.addObserver(o);
	}

	private Observer createGlobalLogger(final SimSettings settings) {
		final GlobalLogger gl = new GlobalLogger(settings.TRACE_OUT_DIR, "global.txt");

		// global logger observer updates the log file when new positions are calculated
		Observer globalLogger = new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				ArrayList<ItemPosition> pos = ((PositionList) arg).getList();
				ArrayList<RobotData> rd = new ArrayList<RobotData>();
				// Add robots
				for(ItemPosition ip : pos) {
					RobotData nextBot = new RobotData(ip.name, ip.x, ip.y, ip.angle, ip.receivedTime);

                    String intValue = ip.name.replaceAll("[^0-9]", ""); // TODO: let's put this as a general method somewhere since it's used so often... (Common?) and so that it handles any renaming of robots that could happen by settings changes (e.g., from "bot" to "robot", etc.)
                    int id = Integer.parseInt(intValue);
                    id = Math.max(0, Math.min(pos.size(), id)); // bound between array indices

                    nextBot.clock = simEngine.logicThreads.get(id ).gvh.clock;
					nextBot.radius = settings.BOT_RADIUS;
					rd.add(nextBot);
				}
				gl.updateData(rd, simEngine.getTime());
			}
		};
		return globalLogger;
	}

	
	private List<List<Object>> resultsList = new ArrayList<List<Object>>();
	/**
	 * Begins executing a simulation. This call will block until the simulation completes.
	 */
	public void start() {
		executor = Executors.newFixedThreadPool(participants.size());
		// Save settings to JSON file
		if(settings.TRACE_OUT_DIR != null)
			SettingsWriter.writeSettings(settings);

		// Invoke all simulated robots
		List<Future<List<Object>>> results = null;
		try {
			if(settings.TIMEOUT > 0)
				results = executor.invokeAll(bots, settings.TIMEOUT, TimeUnit.SECONDS);
			else
				results = executor.invokeAll(bots);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}

		// Wait until all result values are available
		for(Future<List<Object>> f : results) {
			try {
				List<Object> res = f.get();
				if(res != null && !res.isEmpty())
					resultsList.add(res);
			} catch(CancellationException e) {
				// If the executor timed out, the result is cancelled
				System.err.println("Simulation timed out! Execution reached " + settings.TIMEOUT + " sec duration. Aborting.");
				break;
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		shutdown();
	}

	public void shutdown() {
		simEngine.simulationDone();
		executor.shutdownNow();
	}
	
	public void closeWindow() {
		drawFrame.dispose();
	}
	
	public List<List<Object>> getResults() {
		return resultsList;
	}
	
	public long getSimulationDuration() {
		return simEngine.getDuration();
	}
	
	public String getMessageStatistics() {
		return 	simEngine.getComChannel().getStatistics();
	}
}
