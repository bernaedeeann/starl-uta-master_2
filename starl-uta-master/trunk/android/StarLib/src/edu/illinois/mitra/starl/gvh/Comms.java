package edu.illinois.mitra.starl.gvh;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import edu.illinois.mitra.starl.interfaces.Clock;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import edu.illinois.mitra.starl.comms.MessageResult;
import edu.illinois.mitra.starl.comms.RobotMessage;
import edu.illinois.mitra.starl.comms.SmartCommsHandler;
import edu.illinois.mitra.starl.comms.UdpMessage;
import edu.illinois.mitra.starl.interfaces.MessageListener;
import edu.illinois.mitra.starl.interfaces.SmartComThread;
import edu.illinois.mitra.starl.motion.MotionParameters;
import edu.illinois.mitra.starl.objects.Common;

/**
 * Handles all inter-agent communication threads. The Comms class is only
 * instantiated by a GlobalVarHolder.
 * 
 * @author Adam Zimmerman
 * @version 1.0
 * @see GlobalVarHolder
 */
public class Comms {
	private GlobalVarHolder gvh;
	private SmartCommsHandler comms;
	private SmartComThread mConnectedThread;
	private Map<Integer, MessageListener> listeners = Collections.synchronizedMap(new HashMap<Integer, MessageListener>());
	private String name;



    public Comms(GlobalVarHolder gvh, SmartComThread mConnectedThread) {
		this.gvh = gvh;
		this.name = gvh.id.getName();
		this.mConnectedThread = mConnectedThread;
	}

	public void startComms() {
		this.comms = new SmartCommsHandler(gvh, mConnectedThread);
		comms.start();
	}

	public MessageResult addOutgoingMessage(RobotMessage msg, int maxRetries) {
		if(comms != null) {
            // Add the clock to RobotMessage
            gvh.clock.incrementLocal();
            msg.setClock(gvh.clock);
            switch (Common.MESSAGE_TIMING) {
                case MSG_ORDERING_LAMPORT:
                case MSG_ORDERING_VECTOR:
                case MSG_ORDERING_MATRIX: {
                    //System.out.println(name + " is sending message with clock val " + gvh.clock.getClockString() );
                    System.out.println(Integer.toString(gvh.id.getIdNumber()) + ":" + gvh.clock.getClockString() );
                    break;
                }
                case MSG_ORDERING_NONE:
                default: {
                    break;
                }
            }

			// If the message is being sent to myself, add it to the in queue
			if(msg.getTo().equals(name)) {
				addIncomingMessage(msg);
				return new MessageResult(0);
			}

			// Create a new message result object
			int receivers = msg.getTo().equals("ALL") ? gvh.id.getParticipants().size() - 1 : 1;
			MessageResult result = new MessageResult(receivers);

			// Add the message to the queue, link it to the message result object
			comms.addOutgoing(msg, result, maxRetries);

			// Return the message result object
			return result;
		} else {
			return null;
		}
	}

	public MessageResult addOutgoingMessage(RobotMessage msg) {
		return addOutgoingMessage(msg, UdpMessage.DEFAULT_MAX_RETRIES);
	}

	// Message event code
	public void addMsgListener(MessageListener l, int... mid) {
		for(int m : mid)
			addMsgListener(l,m);
	}
	
	public void addMsgListener(MessageListener l, int mid) {
		if(l == null)
			throw new NullPointerException("Can not have a null message listener!");
		
		if(listeners.containsKey(mid)) {
			throw new RuntimeException("Already have a listener for MID " + mid + ", " + listeners.get(mid).getClass().getSimpleName());
		}
		listeners.put(mid, l);
	}

	public void removeMsgListener(int mid) {
		listeners.remove(mid);
	}

    private void printClock(RobotMessage m, String old) {
        switch (Common.MESSAGE_TIMING) {
            case MSG_ORDERING_LAMPORT:
            case MSG_ORDERING_VECTOR:
            case MSG_ORDERING_MATRIX: {
                System.out.println(name + "  has received message with clock " + m.getClock().getClockString() + " and new clock value is " + gvh.clock.getClockString() + " (it was " + old + ")" );
                //System.out.println(name + " " + gvh.clock.greaterThanEqual(m.getClock())); // should be true after receive
                //System.out.println(name + " " + m.getClock().equalTo(gvh.clock)); // should be false
                //System.out.println(name + " " + m.getClock().greaterThan(gvh.clock)); // should be false after receive
                //System.out.println(name + " " + gvh.clock.greaterThan(gvh.clock)); // should always be false
                //System.out.println(name + " " + gvh.clock.greaterThan(m.getClock())); // likely false afterward (since one component equal)
                break;
            }
            case MSG_ORDERING_NONE:
            default: {
                break;
            }
        }
    }

	public void addIncomingMessage(RobotMessage m) {
		if(listeners.containsKey(m.getMID())) {
            //printClock(m);
            String oldClockString = gvh.clock.getClockString();
			listeners.get(m.getMID()).messageReceived(m);
            // update clock when message received
            gvh.clock.handleReceive(m.getClock());
            printClock(m, oldClockString);
		} else {
			gvh.log.e("Critical Error", "No handler for MID " + m.getMID());
		}
	}

	public void stopComms() {
		listeners.clear();
		mConnectedThread.cancel();
		comms = null;
	}

	public void getCommStatistics() {

	}
}
