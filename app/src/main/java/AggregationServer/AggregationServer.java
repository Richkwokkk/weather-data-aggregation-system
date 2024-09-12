package AggregationServer;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;

import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final String DATA_FILE_PATH = "weather_data.txt";
    private static final int DATA_EXPIRATION_TIME = 30000; // 30 seconds
    private LamportClock lamportClock;
    private List<JSONObject> weatherDataList;
    private Map<String, Long> contentServerTimestamps; // Track last contact times for content servers
    
    public AggregationServer(int port) throws IOException {
        this.lamportClock = new LamportClock();
        this.weatherDataList = JSONHandler.parseWeatherData(DATA_FILE_PATH);
        this.contentServerTimestamps = new HashMap<>();

        expireOldData();
        
        try (ServerSocket serverSocket = new ServerSocket(port)) { // Use try-with-resources to close serverSocket
            System.out.println("Aggregation server started on port " + port);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                String request = in.readLine();
                lamportClock.increment(); // Lamport clock tick

                if (request.startsWith("GET")) {
                    handleGetRequest(out);
                } else if (request.startsWith("PUT")) {
                    handlePutRequest(in, out);
                } else {
                    sendResponse(out, "400 Bad Request", 400);
                }
                
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Handle GET request
        private void handleGetRequest(PrintWriter out) throws IOException {
            lamportClock.increment(); // Lamport clock tick
            String jsonData = JSONHandler.convertToJSON(weatherDataList);
            sendResponse(out, jsonData, 200);
        }

        // Handle PUT request
        private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
            StringBuilder putData = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                putData.append(line).append("\n");
            }
            
            lamportClock.increment(); // Lamport clock tick
            try {
                // Parse and validate the incoming weather data
                List<JSONObject> newWeatherData = JSONHandler.parseWeatherData(DATA_FILE_PATH);
                if (newWeatherData.isEmpty()) {
                    sendResponse(out, "204 No Content", 204);
                    return;
                }

                // Update internal storage with new data
                weatherDataList.addAll(newWeatherData);
                saveDataToFile();
                
                // Update content server's timestamp for expiration tracking
                String contentServerId = newWeatherData.get(0).getString("id");
                contentServerTimestamps.put(contentServerId, System.currentTimeMillis());

                // Respond with appropriate status code
                if (weatherDataList.size() == 1) {
                    sendResponse(out, "201 Created", 201); // First data received
                } else {
                    sendResponse(out, "200 OK", 200); // Update received
                }
            } catch (Exception e) {
                sendResponse(out, "500 Internal Server Error", 500); // Invalid data
            }
        }
    }

    // Save weather data to a file
    private void saveDataToFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE_PATH))) {
            for (JSONObject entry : weatherDataList) {
                writer.println(entry.toString());
            }
        }
    }

    // Send a response to the client
    private void sendResponse(PrintWriter out, String message, int statusCode) {
        out.println("HTTP/1.1 " + statusCode + " " + message);
        out.println("Lamport-Clock: " + lamportClock.getClock());
        out.println();
    }

    // Check and expire outdated weather data
    private void expireOldData() {
        long currentTime = System.currentTimeMillis();
        weatherDataList.removeIf(entry -> {
            String contentServerId = entry.getString("id");
            Long lastUpdateTime = contentServerTimestamps.get(contentServerId);
            return lastUpdateTime != null && (currentTime - lastUpdateTime > DATA_EXPIRATION_TIME);
        });
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer(port);
    }
}
