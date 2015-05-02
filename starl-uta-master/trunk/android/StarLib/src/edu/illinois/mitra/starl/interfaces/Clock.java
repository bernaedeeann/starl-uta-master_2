package edu.illinois.mitra.starl.interfaces;

import edu.illinois.mitra.starl.gvh.LamportClock;

/**
 * @author Created by Nathan on 3/6/2015.
 */
public abstract class Clock implements Cloneable {
    /**
     * increment the clock due to a local event
     */
    public abstract void incrementLocal();

    public abstract Clock clone();

    /**
     * increment the clock for the receive message rule, given an input Clock c from the received message
     *
     * For example, c may be either a Lamport clock or a Vector clock and has been sent with the original message
     * @param c the clock value to use in updating this node's clock
     */
    public abstract void handleReceive(Clock c);

    /**
     * Convert the clock value to a string (for adding as a payload into the message)
     * @return
     */
    public abstract String getClockString();

    /**
     * Compare this clock to the given clock c and update this clock
     * @param c the clock to compare this clock to
     */
    public abstract void incrementReceive(Clock c);

    public abstract BooleanPartial greaterThanEqual(Clock c);

    public abstract BooleanPartial greaterThan(Clock c);

    public abstract BooleanPartial equalTo(Clock c);

    public enum BooleanPartial {
        TRUE(true),
        FALSE(false),
        INCOMPARABLE(null);

        private Boolean value;

        private BooleanPartial(Boolean value) {
            this.value = value;
        }

        public Boolean getValue() {
            return value;
        }
    }

    /**
     * Returns true if c1 greater than or equal to c2
     * @param c1
     * @param c2
     */
    public static BooleanPartial greaterThanEqual(Clock c1, Clock c2) {
        return c1.greaterThanEqual(c2);
    }

    /**
     * Returns true if c1 strictly greater than  to c2
     * @param c1
     * @param c2
     */
    public static BooleanPartial greaterThan(Clock c1, Clock c2) {
        return c1.greaterThanEqual(c2);
    }

    /**
     * Returns true if two clocks are equal
     * @param c1
     * @param c2
     */
    public static BooleanPartial equalTo(Clock c1, Clock c2) {
        return c1.equalTo(c2);
    }

    public static class NoClock extends Clock {
        public NoClock() {
           // noop
        }

        public Clock clone() {
            NoClock n = new NoClock();
            return n;
        }

        public void incrementLocal() {
            // noop
        }

        public void handleReceive(Clock C) {
            this.incrementReceive(C);
            this.incrementLocal();
        }

        public String getClockString() {
            return "";
        }

        public void incrementReceive(Clock C) {
            // noop
        }

        /**
         * Returns true if this clock is greater than or equal to c, false if not, and incomparable if it cannot be determined
         * @param c
         * @return
         */
        public BooleanPartial greaterThanEqual(Clock c) {
            return BooleanPartial.FALSE;
        }

        /**
         * Returns true if this clock is strictly greater than to c, false if not, and incomparable if it cannot be determined
         * @param c
         * @return
         */
        public BooleanPartial greaterThan(Clock c) {
            return BooleanPartial.FALSE;
        }

        /**
         * Returns true if this clock is equal to c, false if not, and incomparable if it cannot be determined
         * @param c
         * @return
         */
        public BooleanPartial equalTo(Clock c) { return BooleanPartial.FALSE; }
    }
}
