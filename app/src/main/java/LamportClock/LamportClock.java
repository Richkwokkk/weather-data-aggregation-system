package LamportClock;

public class LamportClock {
    private long time;

    public LamportClock() {
        this.time = 0;
    }

    public synchronized void tick() {
        time++;
    }

    public synchronized void update(long receivedTime) {
        time = Math.max(time, receivedTime) + 1;
    }

    public synchronized long getTime() {
        return time;
    }
}