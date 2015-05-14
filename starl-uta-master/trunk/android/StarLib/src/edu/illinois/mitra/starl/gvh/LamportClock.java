package edu.illinois.mitra.starl.gvh;

import edu.illinois.mitra.starl.interfaces.Clock;

/**
 * @author Created by Nathan on 3/6/2015.
 */
public class LamportClock extends Clock {
    int clockVal;

    public LamportClock() {
        this.clockVal = 0;
    }

    public LamportClock(String newClockVal) {
        try {
            this.clockVal = Integer.parseInt(newClockVal);
        }
        catch (NumberFormatException e) {
            e.printStackTrace(System.err);
        }
    }

    public Clock clone() {
        LamportClock l = new LamportClock();
        l.clockVal = this.clockVal;
        return l;
    }

    public void incrementLocal() {
        this.clockVal++;
    }

    public void handleReceive(Clock C) {
        this.incrementReceive(C);
        this.incrementLocal();
    }

    public String getClockString() {
        return Integer.toString(clockVal);
    }

    public void incrementReceive(Clock C) {
           if(((LamportClock) C).clockVal > this.clockVal) {
               this.clockVal = ((LamportClock) C).clockVal;
           }
    }

    public BooleanPartial greaterThanEqual(Clock c) {
        if (this.clockVal >= ((LamportClock)c).clockVal) {
            return BooleanPartial.TRUE;
        } else {
            return BooleanPartial.FALSE;
        }
    }


    public BooleanPartial greaterThan(Clock c) {
        if (this.clockVal > ((LamportClock)c).clockVal) {
            return BooleanPartial.TRUE;
        } else {
            return BooleanPartial.FALSE;
        }
    }

    public BooleanPartial equalTo(Clock c) {
        if (this.clockVal == ((LamportClock)c).clockVal) {
            return BooleanPartial.TRUE;
        } else {
            return BooleanPartial.FALSE;
        }
    }
}
