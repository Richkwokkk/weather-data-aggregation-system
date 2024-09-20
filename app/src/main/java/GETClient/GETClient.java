package GETClient;

import java.io.*;
import java.net.*;
import java.util.*;
import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class GETClient {
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 5000;
    private final LamportClock lamportClock = new LamportClock();

    protected HttpURLConnection createConnection(String serverUrl) throws IOException {
        URI uri = URI.create(serverUrl);
        return (HttpURLConnection) uri.toURL().openConnection();
    }

    public void getWeatherData(String serverUrl, String stationId) {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                HttpURLConnection connection = createConnection(serverUrl);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock.getTime()));

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String lamportClockHeader = connection.getHeaderField("Lamport-Clock");
                    if (lamportClockHeader != null) {
                        lamportClock.update(Long.parseLong(lamportClockHeader));
                    }
                    lamportClock.tick();

                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String inputLine;
                        StringBuilder response = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        List<Map<String, String>> weatherDataList = JSONHandler.parseJSON(response.toString());
                        displayWeatherData(weatherDataList, stationId);
                    }
                    break;
                } else {
                    System.out.println("GET request failed. Response Code: " + responseCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            retries++;
            if (retries < MAX_RETRIES) {
                System.out.println("Retrying in " + RETRY_DELAY / 1000 + " seconds...");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (retries == MAX_RETRIES) {
            System.out.println("Failed to get weather data after " + MAX_RETRIES + " attempts.");
        }
    }

    private void displayWeatherData(List<Map<String, String>> weatherDataList, String stationId) {
        System.out.println("Current Weather Data:");
        for (Map<String, String> data : weatherDataList) {
            if (stationId == null || stationId.equals(data.get("id"))) {
                System.out.println("--------------------");
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server_url> [station_id]");
            return;
        }
        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;
        new GETClient().getWeatherData(serverUrl, stationId);
    }
}