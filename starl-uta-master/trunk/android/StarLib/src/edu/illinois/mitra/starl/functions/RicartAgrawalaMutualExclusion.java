package edu.illinois.mitra.starl.functions;

import java.util.ArrayList;
import java.util.Set;

import edu.illinois.mitra.starl.comms.MessageContents;
import edu.illinois.mitra.starl.comms.RobotMessage;
import edu.illinois.mitra.starl.gvh.GlobalVarHolder;
import edu.illinois.mitra.starl.interfaces.Clock;
import edu.illinois.mitra.starl.interfaces.MessageListener;
import edu.illinois.mitra.starl.interfaces.MutualExclusion;
import edu.illinois.mitra.starl.objects.Common;

/**
 * Created by Nathan on 3/30/2015.
 */



public class RicartAgrawalaMutualExclusion implements MutualExclusion, MessageListener {

    private static final String TAG = "RicartAgrawalaMutex";
    State state;
    GlobalVarHolder gvh;
    int numBots;
    int num_sections;
    ArrayList<RobotMessage> incomingRequests;
    ArrayList<RobotMessage> incomingReplies;
    String name;

    Clock savedClock;

    public enum State {
        WANTED, HELD, RELEASED
    }


    public RicartAgrawalaMutualExclusion(int num_sections, GlobalVarHolder gvh) {
        this.state = State.RELEASED;
        this.gvh = gvh;
        this.name = gvh.id.getName();
        this.numBots = gvh.id.getParticipants().size();
        this.num_sections = num_sections;
        incomingRequests = new ArrayList<>();
        incomingReplies = new ArrayList<>();
        // Common.MSG_MUTEX_TOKEN: reply that bot is not in CS
        // Common.MSG_MUTEX_TOKEN_REQUEST: request for CS, broadcast to all
        gvh.comms.addMsgListener(this, Common.MSG_MUTEX_TOKEN, Common.MSG_MUTEX_TOKEN_REQUEST);

    }

    public synchronized void requestEntry(int id) {
        if (this.state == State.RELEASED) {
            this.state = State.WANTED;
            System.out.println(gvh.id.getName() + " is requesting the CS");
            // send message to all other nodes
            RobotMessage token_request = new RobotMessage("ALL", name, Common.MSG_MUTEX_TOKEN_REQUEST, new MessageContents(id));
            //token_request.setClock(gvh.clock);
            gvh.comms.addOutgoingMessage(token_request);

            // must do this AFTER multicast (so local clock incremented)
            this.savedClock = gvh.clock.clone(); // saved clock (T in algorithm)
        }
    }

    public synchronized void requestEntry(Set<Integer> ids) {
        for(int id : ids) {
            requestEntry(id);
        }
    }

    public synchronized boolean clearToEnter(int id) {
        if (state == State.WANTED && incomingReplies.size() == (numBots - 1)) {
            for (RobotMessage m : incomingReplies) {
                System.out.println(gvh.id.getIdNumber() + " has replies from: " + m.getFrom());
            }
            incomingReplies.clear();
            state = State.HELD;
            System.out.println("ENTER CS: " + gvh.id.getName() + " is in the CS!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            //System.out.println(gvh.id.getName() " state is now HELD");
            return true;
        }
        return false;
    }

    public synchronized boolean clearToEnter(Set<Integer> ids) {
        boolean retval = true;
        for(int id : ids) {
            retval &= clearToEnter(id);
        }
        return retval;
    }

    public synchronized void exit(int id) {
        if (state == State.HELD) {
            state = State.RELEASED;
            System.out.println(gvh.id.getName() + " has exited CS!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            for (RobotMessage m : incomingRequests) {

                String sentFromBot = m.getFrom();
                RobotMessage reply = new RobotMessage(sentFromBot, name, Common.MSG_MUTEX_TOKEN, new MessageContents(id));
                //reply.setClock(gvh.clock);
                System.out.println("EXIT CS: " + gvh.id.getName() + " notifying " + sentFromBot + " it's not in CS");
                //System.out.println(gvh.id.getName() + " notifying " + sentFromBot + " it's not in CS from exit method");
                gvh.comms.addOutgoingMessage(reply);
            }
            incomingRequests.clear();
        }
    }

    public synchronized void exit(Set<Integer> ids) {
        for(int id : ids) {
            exit(id);
        }
    }

    public synchronized void exitAll() {
        for(int i = 0; i < num_sections; i++) {
            exit(i);
        }
    }

    public void messageReceived(RobotMessage m) {
        int id = Integer.parseInt(m.getContents(0));
        String sentFromBot = m.getFrom();
        String intValue = sentFromBot.replaceAll("[^0-9]", "");
        int fromIDNumber = Integer.parseInt(intValue);

        //Clock.BooleanPartial isClockInGreater = m.getClock().greaterThan(gvh.clock);
        //Clock.BooleanPartial isClockInEqual = m.getClock().equalTo(gvh.clock);
        Clock.BooleanPartial isClockInGreater = m.getClock().greaterThan(this.savedClock);
        Clock.BooleanPartial isClockInEqual = m.getClock().equalTo(this.savedClock);
        boolean isPIDLess = (gvh.id.getIdNumber() < fromIDNumber);
        boolean isThisLessThan = false;
        if(isClockInGreater == Clock.BooleanPartial.TRUE ||
                (isClockInEqual == Clock.BooleanPartial.TRUE && isPIDLess)) {
            isThisLessThan = true;
        }

        switch(m.getMID()) {
            case Common.MSG_MUTEX_TOKEN_REQUEST:
                if(this.state == State.HELD || (this.state == State.WANTED && isThisLessThan)) {
                   //System.out.println(gvh.id.getName() + " holds CS, adding request from " + sentFromBot);
                    System.out.println("REQUEST QUEUED: " + gvh.id.getName() + " adding request from " + sentFromBot + "(" + this.savedClock.getClockString() + "," + gvh.id.getIdNumber() + ") < (" + m.getClockString() + ", " + fromIDNumber + ")");

                    incomingRequests.add(m);

                    //gvh.clock.incrementLocal();
                }

                else {
                    // send a reply to the bot that sent the message
                    RobotMessage reply = new RobotMessage(sentFromBot, name, Common.MSG_MUTEX_TOKEN, new MessageContents(id));
                    System.out.println("REPLY SEND: " + gvh.id.getName() + " notifying " + sentFromBot + " it's not in CS since "  + "(" + this.savedClock.getClockString() + "," + gvh.id.getIdNumber() + ") > (" + m.getClockString() + ", " + fromIDNumber + ")");
                    //System.out.println(gvh.id.getName() + " state is released, notifying " + sentFromBot + " it's not in CS");
                    //reply.setClock(gvh.clock);
                    gvh.comms.addOutgoingMessage(reply);
                }
                break;

            case Common.MSG_MUTEX_TOKEN:
                incomingReplies.add(m);
               // gvh.clock.incrementLocal();
                System.out.println("REPLY RECEIVE: " + gvh.id.getName() + " has received reply from " + sentFromBot +". Size of incoming replies is " + Integer.toString(incomingReplies.size()));
                break;

        }
    }

    @Override
    public void cancel() {
        gvh.trace.traceEvent(TAG, "Cancelled", gvh.time());
        gvh.log.d(TAG, "CANCELLING MUTEX THREAD");

        // Unregister message listeners
        gvh.comms.removeMsgListener(Common.MSG_MUTEX_TOKEN);
        gvh.comms.removeMsgListener(Common.MSG_MUTEX_TOKEN_REQUEST);
    }
}


