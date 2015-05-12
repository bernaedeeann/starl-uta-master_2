package edu.illinois.mitra.demo.projectapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import edu.illinois.mitra.starl.comms.RobotMessage;
import edu.illinois.mitra.starl.functions.RandomLeaderElection;
import edu.illinois.mitra.starl.gvh.GlobalVarHolder;
import edu.illinois.mitra.starl.interfaces.LeaderElection;
import edu.illinois.mitra.starl.interfaces.LogicThread;
import edu.illinois.mitra.starl.objects.*;
import edu.illinois.mitra.starl.motion.*;
import edu.illinois.mitra.starl.motion.MotionParameters.COLAVOID_MODE_TYPE;

public class ProjectApp extends LogicThread {
	private static final boolean RANDOM_DESTINATION = false;
    //private static final boolean RANDOM_DESTINATION = true;
	public static final int ARRIVED_MSG = 22;
    public static final int VISITING_MSG = 33;  //new
    public static final int REQUEST_MSG = 44;   //new
    public static final int REPLY_MSG = 55;    //new
	private static final MotionParameters DEFAULT_PARAMETERS = MotionParameters.defaultParameters();
	private volatile MotionParameters param = DEFAULT_PARAMETERS;
	// this is an ArrayList of HashMap. Each HashMap element in the array will contain one set of waypoints
	final ArrayList<HashMap<String, ItemPosition>> destinations = new ArrayList<>();
    //Arraylist to determine if a robot is going to destination
    //ItemPosition and a boolean to say if a robot is going to it or not
    ArrayList<HashMap<ItemPosition, Boolean>> destinationsRobot = new ArrayList<>();
	private int numSetsWaypoints = 4;
	int robotIndex;
    int my_i;

	// used to find path through obstacles
	Stack<ItemPosition> pathStack;
	RRTNode kdTree = new RRTNode();

	ObstacleList obsList;
	//obsList is a local map each robot has, when path planning, use this map

    PositionList robotPositions;

    ObstacleList obstaclesAll;

	ObstacleList obEnvironment;
	//obEnvironment is the physical environment, used when calculating collisions

	ItemPosition currentDestination, currentDestination1, preDestination;

	private LeaderElection le;
		private String leader = null;
	private boolean iamleader = false;

	private enum Stage {
		PICK, GO, DONE, ELECT, HOLD, MIDWAY
	};

	//private Stage stage = Stage.PICK;
    private Stage stage = Stage.ELECT;

	public ProjectApp(GlobalVarHolder gvh) {
		super(gvh);
		for(int i = 0; i< gvh.gps.getPositions().getNumPositions(); i++){
			if(gvh.gps.getPositions().getList().get(i).name == name){
				robotIndex = i;
				break;
			}

		}

		// instantiates each HashMap object in the array
		for(int i = 0; i < numSetsWaypoints; i++) {
			destinations.add(new HashMap<String, ItemPosition>());
            destinationsRobot.add(new HashMap<ItemPosition, Boolean>());
		}
		le = new RandomLeaderElection(gvh);


		MotionParameters.Builder settings = new MotionParameters.Builder();
		//		settings.ROBOT_RADIUS(400);
		settings.COLAVOID_MODE(COLAVOID_MODE_TYPE.USE_COLBACK);
		MotionParameters param = settings.build();
		gvh.plat.moat.setParameters(param);

		// this loop gets add each set of waypoints i to the hashmap at destinations(i)
		for(ItemPosition i : gvh.gps.getWaypointPositions()) {
			String setNumStr = i.getName().substring(0,1);
			int setNum = Integer.parseInt(setNumStr);
			destinations.get(setNum).put(i.getName(), i);
            destinationsRobot.get(setNum).put(i, false);
		}


		//point the environment to internal data, so that we can update it
		obEnvironment = gvh.gps.getObspointPositions();

		//download from environment here so that all the robots have their own copy of visible ObstacleList
		obsList = gvh.gps.getViews().elementAt(robotIndex) ;

		gvh.comms.addMsgListener(this, ARRIVED_MSG, VISITING_MSG);
	}

	@Override
	public List<Object> callStarL() {
		//int i = 0;
        String key = "0";
        my_i =0;
        int index = Integer.parseInt(name.substring(3));
		while(true) {

			RobotMessage inform; //= new RobotMessage("ALL", name, ARRIVED_MSG, "test");
			//gvh.comms.addOutgoingMessage(inform);

            robotPositions = gvh.gps.getPositions();
            obstaclesAll = new ObstacleList();
           // obstaclesAll = obEnvironment;
            Set<String> participants = gvh.id.getParticipants();
            String[] participantsSet = participants.toArray(new String[0]);
            for(int r = 0; r < participants.size(); r++)
            {
                if(!participantsSet[r].equals(name)) {
                    Obstacles temp = new Obstacles(robotPositions.getPosition(participantsSet[r]).getX(), robotPositions.getPosition(participantsSet[r]).getY());
                    obstaclesAll.addObstacle(temp);
                }
            }
			obEnvironment.updateObs();

			obsList.updateObs();
			if((gvh.gps.getMyPosition().type == 0) || (gvh.gps.getMyPosition().type == 1)){

				switch(stage){
					case ELECT:

						le.elect();
						if(le.getLeader() != null){
							results[1] = le.getLeader();
						}// End of if statement.

						stage = Stage.PICK;

						break;

					case PICK:
						if(destinations.get(my_i).isEmpty()){
							if(my_i+1 >= numSetsWaypoints)
								stage = Stage.DONE;
							else
								my_i++;
						}// End of if statement.
						else{

							//RobotMessage informleader = new RobotMessage("ALL", name, 21, le.getLeader());
						   //gvh.comms.addOutgoingMessage(informleader);

							iamleader = le.getLeader().equals(name);
							//iamleader = true;

							if(iamleader) {
/*
                                double leastDist = 5000;
                                int leastDistNum = 0;
                                index = 0;
                                if (currentDestination == null) {
                                    leastDistNum = 0;
                                } else {
                                    //leastDistNum = Integer.parseInt(currentDestination.getName().substring(1));
                                }
                                double dist;
                                ItemPosition destination;
                                int destSize = destinations.get(my_i).size();

                                for (int c = 0; c < destSize; c++) {
                                    key = Integer.toString(my_i);
                                    key += c;
                                    destination = destinations.get(my_i).get(key);
                                    if (destination != null) {
                                        dist = Math.sqrt(((Math.pow((double) (destination.getX() - gvh.gps.getPosition(name).getX()), 2) + Math.pow((double) (destination.getY() - gvh.gps.getPosition(name).getY()), 2))));

                                        if (dist < leastDist) {
                                            leastDist = dist;
                                            leastDistNum = c;
                                        }
                                    }
                                }
                                //check if this destination is set to true in destinationRobot
                                //get the next available destination in set
                                key = Integer.toString(my_i);
                                key += leastDistNum;
                                currentDestination = destinations.get(my_i).get(key);
                                String setNumStr = currentDestination.getName().substring(0, 1);
                                int setNum = Integer.parseInt(setNumStr);
                                if (destinationsRobot.get(setNum).get(currentDestination) == true)
                                {
                                    for(int w =0; w < destinationsRobot.get(my_i).size(); w++)
                                    {
                                        key = Integer.toString(my_i);
                                        key += w;
                                        currentDestination1 = destinations.get(my_i).get(key);
                                        if(destinationsRobot.get(setNum).get(currentDestination1) == false)
                                        {
                                            currentDestination = currentDestination1;
                                            break;
                                        }
                                    }
                                }
*/
                                //So robots can save this because it is going to be visited
                                key = Integer.toString(my_i);
                                key += this.name.substring(3);
                                currentDestination = destinations.get(my_i).get(key);
                                String setNumStr = currentDestination.getName().substring(0,1);
                                int setNum = Integer.parseInt(setNumStr);
                                destinationsRobot.get(setNum).put(currentDestination, true);
                                inform = new RobotMessage("ALL", name, VISITING_MSG, currentDestination.getName());
                                gvh.comms.addOutgoingMessage(inform);
                                //trying to combine robot positions with environment position
                                robotPositions = gvh.gps.getPositions();
                                obstaclesAll = new ObstacleList();
                                // obstaclesAll = obEnvironment;
                                participants = gvh.id.getParticipants();
                                participantsSet = participants.toArray(new String[0]);
                                for(int r = 0; r < participants.size(); r++)
                                {
                                    if(!participantsSet[r].equals(name)) {
                                        Obstacles temp = new Obstacles(robotPositions.getPosition(participantsSet[r]).getX(), robotPositions.getPosition(participantsSet[r]).getY());
                                        obstaclesAll.addObstacle(temp);
                                    }
                                }
                                obstaclesAll.addObstacles(obEnvironment.ObList);
								RRTNode path = new RRTNode(gvh.gps.getPosition(name).x, gvh.gps.getPosition(name).y);
								// Old code.
							//pathStack = path.findRoute(currentDestination, 5000, obsList, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));
								// New code.
								//pathStack = path.findRoute(currentDestination, 5000, obEnvironment, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));
                                pathStack = path.findRoute(currentDestination, 5000, obstaclesAll, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));

                                kdTree = RRTNode.stopNode;
								//wait when can not find path
								if(pathStack == null){
									stage = Stage.HOLD;
								}// End of if statement.
								else{
									preDestination = null;
									stage = Stage.MIDWAY;
								}// End of else statement.
							}// End of if statement.

						else
						{
/*
                            //check for the closest destination and set current destination to that one
                            double leastDist = 5000;
                            int leastDistNum = 0;
                            index = 0;
                            if (currentDestination == null) {
                                leastDistNum = 0;
                            } else {
                                //leastDistNum = Integer.parseInt(currentDestination.getName().substring(1));
                            }
                            double dist;
                            ItemPosition destination;
                            int destSize = destinations.get(my_i).size();

                            for (int c = 0; c < destSize; c++) {
                                key = Integer.toString(my_i);
                                key += c;
                                destination = destinations.get(my_i).get(key);
                                if (destination != null) {
                                    dist = Math.sqrt(((Math.pow((double) (destination.getX() - gvh.gps.getPosition(name).getX()), 2) + Math.pow((double) (destination.getY() - gvh.gps.getPosition(name).getY()), 2))));

                                    if (dist < leastDist) {
                                        leastDist = dist;
                                        leastDistNum = c;
                                    }
                                }
                            }
                            //check if this destination is set to true in destinationRobot
                            //get the next available destination in set
                            key = Integer.toString(my_i);
                            key += leastDistNum;
                            currentDestination = destinations.get(my_i).get(key);
                            String setNumStr = currentDestination.getName().substring(0, 1);
                            int setNum = Integer.parseInt(setNumStr);
                            if (destinationsRobot.get(setNum).get(currentDestination) == true)
                            {
                                    for(int w =0; w < destinationsRobot.get(my_i).size(); w++)
                                    {
                                        key = Integer.toString(my_i);
                                        key += w;
                                        currentDestination1 = destinations.get(my_i).get(key);
                                        if(destinationsRobot.get(setNum).get(currentDestination1) == false)
                                        {
                                            currentDestination = currentDestination1;
                                            break;
                                        }
                                    }
                             }
*/
                            key = Integer.toString(my_i);
                            key += this.name.substring(3);
                            currentDestination = destinations.get(my_i).get(key);
                            String setNumStr = currentDestination.getName().substring(0,1);
                            int setNum = Integer.parseInt(setNumStr);
                            destinationsRobot.get(setNum).put(currentDestination, true);
                            inform = new RobotMessage("ALL", name, VISITING_MSG, currentDestination.getName());
                            gvh.comms.addOutgoingMessage(inform);
                            //trying to combine robot positions with environment position
                            robotPositions = gvh.gps.getPositions();
                            obstaclesAll = new ObstacleList();
                            // obstaclesAll = obEnvironment;
                            participants = gvh.id.getParticipants();
                            participantsSet = participants.toArray(new String[0]);
                            for(int r = 0; r < participants.size(); r++)
                            {
                                if(!participantsSet[r].equals(name)) {
                                    Obstacles temp = new Obstacles(robotPositions.getPosition(participantsSet[r]).getX(), robotPositions.getPosition(participantsSet[r]).getY());
                                    obstaclesAll.addObstacle(temp);
                                }
                            }
                            obstaclesAll.addObstacles(obEnvironment.ObList);

                            RRTNode path = new RRTNode(gvh.gps.getPosition(name).x, gvh.gps.getPosition(name).y);
                            // Old code.
                            //pathStack = path.findRoute(currentDestination, 5000, obsList, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));
                            // New code.
                           //pathStack = path.findRoute(currentDestination, 5000, obEnvironment, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));
                            pathStack = path.findRoute(currentDestination, 5000, obstaclesAll, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));

                            kdTree = RRTNode.stopNode;
                            //wait when can not find path
                            if(pathStack == null){
                                stage = Stage.HOLD;
                            }// End of if statement.
                            else{
                                preDestination = null;
                                stage = Stage.MIDWAY;
                            }// End of else statement.
                            //Robots in hold the whole time
						/*currentDestination = gvh.gps.getPosition(le.getLeader());
						currentDestination1 = new ItemPosition(currentDestination);
						int newx, newy;
						if(gvh.gps.getPosition(name).getX() < currentDestination1.getX())
						{
							newx = gvh.gps.getPosition(name).getX() - currentDestination1.getX()/8;
						}
						else
						{
							newx = gvh.gps.getPosition(name).getX() + currentDestination1.getX()/8;
						}
						if(gvh.gps.getPosition(name).getY() < currentDestination1.getY())
						{
							newy = gvh.gps.getPosition(name).getY() - currentDestination1.getY()/8;
						}
						else
						{
							newy = gvh.gps.getPosition(name).getY() + currentDestination1.getY()/8;
						}
						currentDestination1.setPos(newx, newy, (currentDestination1.getAngle()));
						currentDestination1.setPos(currentDestination);
						gvh.plat.moat.goTo(currentDestination1, obsList);
						//stage = Stage.HOLD;*/
						}

                            key = Integer.toString(my_i);
						}
						break;


					case MIDWAY:
						if(!gvh.plat.moat.inMotion){
							if(pathStack == null){
								stage = Stage.HOLD;
								// if can not find a path, wait for obstacle map to change
								break;
							}// End of if statement.
							if(!pathStack.empty()){
								//if did not reach last midway point, go back to path planning
								if(preDestination != null){
									if((gvh.gps.getPosition(name).distanceTo(preDestination)>param.GOAL_RADIUS)){
										pathStack.clear();
										stage = Stage.PICK;
										break;
									}// End of if statement.
									preDestination = pathStack.peek();
								}// End of if statement.
								else{
									preDestination = pathStack.peek();
								}// End of else statement.
								ItemPosition goMidPoint = pathStack.pop();
								// Old code.
								gvh.plat.moat.goTo(goMidPoint, obsList);
								// New code.
								//gvh.plat.moat.goTo(goMidPoint, obEnvironment);
								stage = Stage.MIDWAY;
							}// End of if statement.
							else{
								if((gvh.gps.getPosition(name).distanceTo(currentDestination)>param.GOAL_RADIUS)){
									pathStack.clear();
									stage = Stage.PICK;
								}
								else{
									if(currentDestination != null){
                                        //GOES TO HEAR WHEN LANDS ON DOT
                                        //Freeze robot until other robots pick up all the set
                                        destinations.get(my_i).remove(currentDestination.getName());
										/*RobotMessage*/
                                        inform = new RobotMessage("ALL", name, ARRIVED_MSG, currentDestination.getName());
                                        gvh.comms.addOutgoingMessage(inform);
                                        //Old Code
                                        //stage = Stage.PICK;
                                        if(destinations.get(my_i).isEmpty()) {
                                            //Old Code
                                            stage = Stage.PICK;
                                        }
                                        else
                                        {
                                            stage = Stage.HOLD;
                                        }
									}// End of if statement.
								}// End of else statement.
							}// End of else statement.
						}// End of if statement.
						break;

					case GO:
						if(!gvh.plat.moat.inMotion) {

                                destinations.get(my_i).remove(currentDestination.getName());
										/*RobotMessage*/
                                inform = new RobotMessage("ALL", name, ARRIVED_MSG, currentDestination.getName());
                                gvh.comms.addOutgoingMessage(inform);
                            //Old Code
							stage = Stage.PICK;
						}

						break;
					case HOLD:
                        //new code
						if(destinations.get(my_i).isEmpty()){
							stage = Stage.PICK;
					    }
						else
						{
							gvh.plat.moat.motion_stop();
						}
						break;

					case DONE:
						gvh.plat.moat.motion_stop();
						return null;
				}
			}
			else{
                key += index;
				currentDestination = getRandomElement(destinations.get(my_i));
				// Old code.
				gvh.plat.moat.goTo(currentDestination, obsList);
				// New code.
				//gvh.plat.moat.goTo(currentDestination, obEnvironment);
			}
			sleep(100);
		}
	}

	@Override
	protected void receive(RobotMessage m) {
		String posName = m.getContents(0);
		System.out.println("message test receive");
        switch(m.getMID()) {
            case(ARRIVED_MSG):
            if (destinations.get(my_i).containsKey(posName))
                destinations.get(my_i).remove(posName);
            if(destinations.get(my_i).isEmpty()){
                 stage = Stage.PICK;
            }
            //if (currentDestination.getName().equals(posName)) {
               // gvh.plat.moat.cancel();
                //stage = Stage.PICK;
           // }
                break;
            case(VISITING_MSG):
                    currentDestination1 = destinations.get(my_i).get(posName);
                    String setNumStr = currentDestination1.getName().substring(0,1);
                    int setNum = Integer.parseInt(setNumStr);
                    destinationsRobot.get(setNum).put(currentDestination1, true);
                    stage = Stage.MIDWAY;
                break;
        }

	}

	private static final Random rand = new Random();

	@SuppressWarnings("unchecked")
	private <X, T> T getRandomElement(Map<X, T> map) {
		if(RANDOM_DESTINATION)
			return (T) map.values().toArray()[rand.nextInt(map.size())];
		else
			return (T) map.values().toArray()[0];
	}
}