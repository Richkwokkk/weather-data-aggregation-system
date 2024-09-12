package LamportClock;

public class LamportClock {
    private int clock;

    public LamportClock() {
        this.clock = 0;
    }

    // Increment local clock
    public synchronized void increment() {
        this.clock++;
    }

    // Update clock based on received timestamp
    public synchronized void update(int receivedClock) {
        this.clock = Math.max(this.clock, receivedClock) + 1;
    }

    // Get current clock value
    public synchronized int getClock() {
        return this.clock;
    }
}
