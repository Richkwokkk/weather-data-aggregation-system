package GETClient;

import java.io.*;
import java.net.*;
import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class GETClient {

    private LamportClock lamportClock;

    public GETClient() {
        this.lamportClock = new LamportClock();
    }

    public static void main(String[] args) {
        // Parse command line args for server name and port
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server:port> [<stationID>]");
            return;
        }
        
        String serverName = args[0];
        String stationID = (args.length > 1) ? args[1] : null;

        // Initialize the client
        GETClient client = new GETClient();
        client.sendGETRequest(serverName, stationID);
    }

    private void sendGETRequest(String serverName, String stationID) {
        try {
            // Extract server host and port
            String[] serverDetails = serverName.split(":");
            String host = serverDetails[0];
            int port = Integer.parseInt(serverDetails[1]);

            // Establish connection to server
            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Increment the Lamport clock before sending the request
            lamportClock.increment();
            long lamportTimestamp = lamportClock.getClock();

            // Formulate GET request
            StringBuilder request = new StringBuilder("GET /weather.json HTTP/1.1\r\n");
            request.append("Host: ").append(serverName).append("\r\n");
            request.append("User-Agent: GETClient/1.0\r\n");
            request.append("Connection: close\r\n");
            request.append("Lamport-Timestamp: ").append(lamportTimestamp).append("\r\n");

            if (stationID != null) {
                request.append("Station-ID: ").append(stationID).append("\r\n");
            }

            request.append("\r\n");

            // Send the request to the server
            out.print(request.toString());
            out.flush();

            // Handle the response
            handleResponse(in);

            // Close resources
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            System.err.println("Error during communication: " + e.getMessage());
        }
    }

    private void handleResponse(BufferedReader in) throws IOException {
        String responseLine;
        StringBuilder responseData = new StringBuilder();
        int receivedLamportTimestamp = -1;

        // Read and store response
        while ((responseLine = in.readLine()) != null) {
            // Check for Lamport timestamp in response headers
            if (responseLine.startsWith("Lamport-Timestamp:")) {
                receivedLamportTimestamp = Integer.parseInt(responseLine.split(":")[1].trim());
            }
            responseData.append(responseLine);
            responseData.append("\n");
        }

        // Synchronize the Lamport clock with the server's timestamp
        if (receivedLamportTimestamp != -1) {
            lamportClock.update(receivedLamportTimestamp);
        }

        // Assuming the HTTP headers end with two line breaks, we extract the body
        String responseString = responseData.toString();
        String jsonBody = responseString.substring(responseString.indexOf("\r\n\r\n") + 4);

        // Parse and display the weather data using JSONHandler
        JSONHandler.displayWeatherData(jsonBody);

        // Increment Lamport Clock after processing the response
        lamportClock.increment();
    }
}