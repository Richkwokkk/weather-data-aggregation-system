package ContentServer;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class ContentServer {
    private String serverName;
    private int port;
    private String filePath;
    private LamportClock lamportClock;

    public ContentServer(String serverName, int port, String filePath) {
        this.serverName = serverName;
        this.port = port;
        this.filePath = filePath;
        this.lamportClock = new LamportClock();
    }

    public void start() {
        try {
            // Create a socket connection to the Aggregation Server
            Socket socket = new Socket(serverName, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Read and parse the file using JSONHandler
            List<JSONObject> weatherDataList = JSONHandler.parseWeatherData(filePath);

            // Convert JSON objects to a single JSON array string
            String jsonData = new JSONArray(weatherDataList).toString();

            // Include Lamport Clock value in PUT request headers
            sendPutRequest(out, jsonData);

            // Handle response and update Lamport Clock
            handleResponse(in);

            // Close resources
            out.close();
            in.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleResponse(BufferedReader in) throws IOException {
        String responseLine;
        StringBuilder responseHeaders = new StringBuilder();
        int statusCode = -1;

        while ((responseLine = in.readLine()) != null) {
            if (responseLine.isEmpty()) {
                // End of headers
                break;
            }
            responseHeaders.append(responseLine).append("\n");
            // Extract status code from the response status line
            if (responseLine.startsWith("HTTP/1.1")) {
                statusCode = Integer.parseInt(responseLine.split(" ")[1]);
            }
        }

        // Handle the status code
        switch (statusCode) {
            case 201:
                System.out.println("Received status 201: Created");
                break;
            case 200:
                System.out.println("Received status 200: OK");
                break;
            case 204:
                System.out.println("Received status 204: No Content");
                break;
            case 400:
                System.out.println("Received status 400: Bad Request");
                break;
            case 500:
                System.out.println("Received status 500: Internal Server Error");
                break;
            default:
                System.out.println("Received unknown status code: " + statusCode);
                break;
        }

        // If Lamport Clock is included in headers, update it
        if (responseHeaders.toString().contains("X-Lamport-Clock:")) {
            int receivedLamportClock = Integer.parseInt(responseHeaders.toString().split("X-Lamport-Clock:")[1].split("\n")[0].trim());
            lamportClock.update(receivedLamportClock);
        } else {
            lamportClock.increment(); // Increment if no Lamport Clock header is present
        }
    }
    

    private void sendPutRequest(PrintWriter out, String jsonData) {
        // Construct headers
        out.println("PUT /weather.json HTTP/1.1");
        out.println("User-Agent: ATOMClient/1/0");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println("X-Lamport-Clock: " + lamportClock.getClock()); // Add Lamport Clock header
        out.println(); // Blank line to separate headers from body
        out.println(jsonData);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java ContentServer <server_name> <port> <file_path>");
            System.exit(1);
        }

        String serverName = args[0];
        int port = Integer.parseInt(args[1]);
        String filePath = args[2];

        ContentServer contentServer = new ContentServer(serverName, port, filePath);
        contentServer.start();
    }
}
