package edu.illinois.mitra.demo.projectapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
	public static final int ARRIVED_MSG = 22;
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
	ObstacleList obEnvironment;
	//obEnvironment is the physical environment, used when calculating collisions

	ItemPosition currentDestination, currentDestination1, preDestination;

	//private LeaderElection le;
		//private String leader = null;
	private boolean iamleader = false;

	private enum Stage {
		PICK, GO, DONE, ELECT, HOLD, MIDWAY
	};

	private Stage stage = Stage.PICK;

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
		//le = new RandomLeaderElection(gvh);


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

		gvh.comms.addMsgListener(this, ARRIVED_MSG);
	}

	@Override
	public List<Object> callStarL() {
		//int i = 0;
        String key = "0";
        my_i =0;
        int index = Integer.parseInt(name.substring(3));
		while(true) {

			RobotMessage inform = new RobotMessage("ALL", name, ARRIVED_MSG, "test");
			gvh.comms.addOutgoingMessage(inform);

			obEnvironment.updateObs();

			obsList.updateObs();
			if((gvh.gps.getMyPosition().type == 0) || (gvh.gps.getMyPosition().type == 1)){

				switch(stage){
					case ELECT:

						//le.elect();
						//if(le.getLeader() != null){
						//	results[1] = le.getLeader();
						//}// End of if statement.

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

							//iamleader = le.getLeader().equals(name);
							iamleader = true;

							if(iamleader) {
                                //setting current destination
                                //key = Integer.toString(my_i);
                                //key += robotIndex;
                                //currentDestination= destinations.get(my_i).get(key);

                                //check for the closest destination and set current destination to that one
                                double leastDist = 5000;
                                int leastDistNum = 0;
                                index = 0;
                                if (currentDestination == null) {
                                    leastDistNum = 0;
                                } else {
                                    leastDistNum = Integer.parseInt(currentDestination.getName().substring(1));

                                }
                                double dist;
                                ItemPosition destination;
                                int destSize = destinations.get(my_i).size();

                                for (int c = 0; c < destSize; c++) {
                                    key = Integer.toString(my_i);
                                    index = 0;
                                    if (currentDestination == null) {
                                        key += c;
                                    } else {
                                        if ((leastDistNum + 1) <= destSize) {
                                            if ((leastDistNum + 1) < destSize) {
                                                index = c + 1;
                                                key += index;
                                            } else {
                                                if (c >= leastDistNum) {
                                                    index = c + 1;
                                                    key += index;
                                                } else {
                                                    key += c;
                                                }

                                            }
                                        } else {
                                            key += c;
                                        }
                                    }
                                    destination = destinations.get(my_i).get(key);
                                    //check if this destination is set to true in destinationRobot
                                    //if a robot is already going to this destination no need to check the distance
                                    //get the next availble destination in set
                                    if (destination != null) {
                                        dist = Math.sqrt(((Math.pow((double) (destination.getX() - gvh.gps.getPosition(name).getX()), 2) + Math.pow((double) (destination.getY() - gvh.gps.getPosition(name).getY()), 2))));

                                        if (dist < leastDist) {
                                            leastDist = dist;
                                            leastDistNum = c;
                                        }
                                    }
                                }
                                key = Integer.toString(my_i);
                                key += leastDistNum;
                                currentDestination = destinations.get(my_i).get(key);
                                //String setNumStr = currentDestination.getName().substring(0, 1);
                                //int setNum = Integer.parseInt(setNumStr);
                                //if (destinationsRobot.get(setNum).get(currentDestination) == true) {
                                  //  int newDest = 0;
                                    //while (newDest == 0) {
                                      //  currentDestination = getRandomElement(destinations.get(my_i));
                                       // if (destinationsRobot.get(setNum).get(currentDestination) == false) {
                                         //   newDest = 1;
                                       // }
                                    //}
                               // }

                                //setNumStr = currentDestination.getName().substring(0,1);
                                //setNum = Integer.parseInt(setNumStr);
                                //destinationsRobot.get(setNum).put(currentDestination, true);
                                if(currentDestination == null)
                                {
                                    //check that this one isn't set to true either
                                    currentDestination = getRandomElement(destinations.get(my_i));
                                    //setNumStr = currentDestination.getName().substring(0,1);
                                    //setNum = Integer.parseInt(setNumStr);
                                    //destinationsRobot.get(setNum).put(currentDestination, true);
                                }
								RRTNode path = new RRTNode(gvh.gps.getPosition(name).x, gvh.gps.getPosition(name).y);
								// Old code.
							pathStack = path.findRoute(currentDestination, 5000, obsList, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));
								// New code.
								//pathStack = path.findRoute(currentDestination, 5000, obEnvironment, 5000, 3000, (gvh.gps.getPosition(name)), (int) (gvh.gps.getPosition(name).radius*0.8));

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
                        /*
						else
						{
						//currentDestination = gvh.gps.getPosition(le.getLeader());
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
//						currentDestination1.setPos(currentDestination);
						gvh.plat.moat.goTo(currentDestination1, obsList);
						stage = Stage.HOLD;
						}
						*/
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
//								gvh.plat.moat.goTo(goMidPoint, obEnvironment);
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
                                        stage = Stage.PICK;
                                        //new code
                                        //stage= Stage.HOLD;
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
                            //new code
                            //stage= Stage.HOLD;
						}

						break;
					case HOLD:
                        //new code
						//if(destinations.get(my_i).isEmpty()){
							stage = Stage.PICK;
					    //}
						//else
						//{
							gvh.plat.moat.motion_stop();
						//}
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
//				gvh.plat.moat.goTo(currentDestination, obEnvironment);
			}
			sleep(100);
		}
	}

	@Override
	protected void receive(RobotMessage m) {
		String posName = m.getContents(my_i);
		System.out.println("message test receive");
		if(destinations.get(my_i).containsKey(posName))
			destinations.get(my_i).remove(posName);

		if(currentDestination.getName().equals(posName)) {
			gvh.plat.moat.cancel();
			stage = Stage.PICK;
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