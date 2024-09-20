package ContentServer;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class ContentServer {
    private static final int UPDATE_INTERVAL = 15000;
    private static final int RETRY_DELAY = 5000;
    private final LamportClock lamportClock = new LamportClock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final String serverUrl;
    private final String filePath;
    private Map<String, String> weatherData;

    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = serverUrl;
        this.filePath = filePath;
    }

    public void start() {
        loadWeatherData();
        scheduler.scheduleAtFixedRate(this::sendUpdate, 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void loadWeatherData() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            weatherData = new HashMap<>();
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    weatherData.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected HttpURLConnection createConnection(String url) throws IOException {
        return (HttpURLConnection) URI.create(url).toURL().openConnection();
    }

    private int maxRetries = 3;

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    private void sendUpdate() {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                HttpURLConnection connection = createConnection(serverUrl);
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock.getTime()));

                lamportClock.tick();
                weatherData.put("timestamp", String.valueOf(lamportClock.getTime()));

                List<Map<String, String>> dataList = new ArrayList<>();
                dataList.add(weatherData);
                String jsonData = JSONHandler.convertToJSON(dataList);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonData.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    System.out.println("Update sent successfully");
                    String lamportClockHeader = connection.getHeaderField("Lamport-Clock");
                    if (lamportClockHeader != null) {
                        lamportClock.update(Long.parseLong(lamportClockHeader));
                    }
                    break;
                } else {
                    System.out.println("Failed to send update. Response Code: " + responseCode);
                }

                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            retries++;
            if (retries < maxRetries) {
                System.out.println("Retrying in " + RETRY_DELAY / 1000 + " seconds...");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (retries == maxRetries) {
            System.out.println("Failed to send update after " + maxRetries + " attempts.");
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ContentServer <server_url> <file_path>");
            return;
        }
        new ContentServer(args[0], args[1]).start();
    }

    public void testSendUpdate() {
        sendUpdate();
    }
    
}