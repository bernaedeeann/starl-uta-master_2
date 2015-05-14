package edu.illinois.mitra.starl.gvh;

import java.util.Arrays;

import edu.illinois.mitra.starl.interfaces.Clock;

/**
 * Created by Nathan on 3/8/2015.
 */
public class VectorClock extends Clock {
    // clock index is the id associated with the robot and the index used when incrementing the VC
    int clockIndex;
    int[] clockVals;

    public VectorClock(int numRobots, int robotID) {
        this.clockIndex = robotID;
        this.clockVals = new int[numRobots];
        for(int i = 0; i < numRobots; i++ ) {
            clockVals[i] = 0;
        }
    }

    public Clock clone() {
        VectorClock v = new VectorClock(this.clockVals.length, this.clockIndex);
        v.clockVals = this.clockVals.clone();
        return v;
    }

    /**
     * this will only be called when SmartCommsHandler clones the RobotMessage sent from another bot
     * @param newClockVals
     */
    public VectorClock(String newClockVals) {
        // convert string to array
        String[] items = newClockVals.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\ ", "").split(",");
        int[] results = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = items[i].trim();
            try {
                results[i] = Integer.parseInt(items[i]);
            } catch (NumberFormatException nfe) {};
        }

        this.clockVals = results;
        // the clockIndex will not be sent with the RobotMessage
        // since the recipient doesn't care who sent the message, just set it to zero
        // probably not the best way to handle this
        this.clockIndex = 0;
    }

    public void incrementLocal() {
        this.clockVals[clockIndex]++;
    }

    public void handleReceive(Clock C) {
        this.incrementReceive(C);
        this.incrementLocal();
    }

    public String getClockString() {
        return Arrays.toString(clockVals);
    }

    public void incrementReceive(Clock C) {
        int[] clockIn = ((VectorClock) C).clockVals;
        for( int i = 0; i < clockIn.length; i++ ) {
            if(clockIn[i] > this.clockVals[i]) {
                this.clockVals[i] = clockIn[i];
            }
        }
    }

    public BooleanPartial greaterThanEqual(Clock C) {
        int[] clockIn = ((VectorClock) C).clockVals;
        BooleanPartial retval = BooleanPartial.TRUE;
        for (int i = 0; i < clockVals.length; i++) {
            // greater-than-or-equal if every component holds, else, need to see if incomparable (could be C >= this)
            if (!(this.clockVals[i] >= clockIn[i])) {
                retval = BooleanPartial.INCOMPARABLE;
                break;
            }
        }

        if (retval == BooleanPartial.INCOMPARABLE) {
            boolean lt = true; // start by assuming it is less than
            //retval = C.greaterThanEqual(this); // TODO: check infinite loop (did hit this case)

            // check less-than-or-equal case (to return false)
            for (int i = 0; i < clockVals.length; i++) {
                // greater-than-or-equal if every component holds, else, need to see if incomparable (could be C >= this)
                if (!(this.clockVals[i] <= clockIn[i])) {
                    lt = false; // incomparable, don't change retval
                }
            }

            // this was less-than-or-equal to C
            if (lt) {
                retval = BooleanPartial.FALSE;
            }
        }

        return retval;
    }

    public BooleanPartial greaterThan(Clock C) {
        BooleanPartial retval;

        if (this.greaterThanEqual(C) == BooleanPartial.TRUE && this.equalTo(C) == BooleanPartial.FALSE) {
            retval = BooleanPartial.TRUE;
        } else if (this.greaterThanEqual(C) == BooleanPartial.TRUE && this.equalTo(C) == BooleanPartial.TRUE) {
            retval = BooleanPartial.FALSE;
        }
        else {
            retval = BooleanPartial.INCOMPARABLE;
        }

        return retval;
    }

    public BooleanPartial equalTo(Clock C) {
        int[] clockIn = ((VectorClock) C).clockVals;
        BooleanPartial retval = BooleanPartial.TRUE;
        for (int i = 0; i < clockVals.length; i++) {
            if (this.clockVals[i] != clockIn[i]) {
                retval = BooleanPartial.FALSE;
                break;
            }
        }

        return retval;
    }
}
