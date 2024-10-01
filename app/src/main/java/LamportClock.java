import java.io.IOException;
import java.util.logging.*;
import java.nio.file.Paths;
import java.nio.file.Files;

/**
 * Represents a Lamport Clock for logical time in distributed systems.
 * This class provides methods to manage and log logical time events.
 */
public class LamportClock {
    private String time;
    private int value;

    /**
     * Constructs a new LamportClock instance.
     * Initializes the time with current system time and value to 0.
     */
    public LamportClock() {
        this.time = String.format("%04d", System.currentTimeMillis());
        this.value = 0;
    }

    /**
     * Increments the logical clock value.
     * This method is synchronized to ensure thread-safety.
     */
    public synchronized void tick() {
        value++;
    }

    /**
     * Updates the logical clock value based on a received value.
     * This method is synchronized to ensure thread-safety.
     *
     * @param newValue The received logical clock value
     */
    public synchronized void update(int newValue) {
        // Set the value to the maximum of the received value + 1 and the current value + 1
        value = Math.max(newValue + 1, value + 1);
    }

    /**
     * Retrieves the current logical clock value.
     *
     * @return The current value of the logical clock
     */
    public int getValue() {
        return value;
    }

    /**
     * Logs an event with the current logical clock value.
     *
     * @param eventName The name of the event to be logged
     */
    public void log(String eventName) {
        Logger logger = Logger.getLogger("lamportlogger");
        String logDirPath = "logs";
        String logFilePath = logDirPath + "/" + this.time + ".log";
        FileHandler handler = null;
        try {
            // Create the log directory if it doesn't exist
            Files.createDirectories(Paths.get(logDirPath));
            
            // Set up the file handler for logging
            handler = new FileHandler(logFilePath, true);
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            
            // Set up the formatter for log messages
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
            
            // Log the event with the current logical clock value
            logger.info(eventName + " " + value);
        } catch (IOException e) {
            // Print error message if logging fails
            System.err.println("Failed to log event: " + e.getMessage());
        } finally {
            // Ensure the handler is closed to release resources
            if (handler != null) {
                handler.close();
            }
        }
    }
}