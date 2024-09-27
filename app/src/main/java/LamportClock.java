import java.io.IOException;
import java.util.logging.*;
import java.nio.file.Paths;
import java.nio.file.Files;

public class LamportClock {
    private String time;
    private int value;

    public LamportClock() {
        this.time = String.format("%04d", System.currentTimeMillis());
        this.value = 0;
    }

    public synchronized void tick() {
        value++;
    }

    public synchronized void update(int newValue) {
        value = Math.max(newValue + 1, value + 1);
    }

    public int getValue() {
        return value;
    }

    public void log(String eventName) {
        Logger logger = Logger.getLogger("lamportlogger");
        String logDirPath = "logs";
        String logFilePath = logDirPath + "/" + this.time + ".log";
        FileHandler handler = null;
        try {
            Files.createDirectories(Paths.get(logDirPath));
            
            handler = new FileHandler(logFilePath, true);
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
            logger.info(eventName + " " + value);
        } catch (IOException e) {
            System.err.println("Failed to log event: " + e.getMessage());
        } finally {
            if (handler != null) {
                handler.close();
            }
        }
    }
}