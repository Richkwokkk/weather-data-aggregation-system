import java.io.*;
import java.net.Socket;
import org.json.JSONObject;

/**
 * GETClient class for sending HTTP GET requests and processing responses.
 */
public class GETClient {
    private static LamportClock clock = new LamportClock();
    private NetworkClient networkClient;

    public interface NetworkClient {
        JSONObject sendRequest(String address, int port, String request) throws IOException;
    }

    // Constructor with custom NetworkClient
    public GETClient(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    // Default constructor using DefaultNetworkClient
    public GETClient() {
        this.networkClient = new DefaultNetworkClient();
    }

    /**
     * Sends an HTTP GET request and processes the response.
     * @param address The server address
     * @param port The server port
     * @param request The HTTP request string
     * @return JSONObject containing the response data, or null if the request fails
     */
    public JSONObject request(String address, int port, String request) throws IOException {
        return networkClient.sendRequest(address, port, request);
    }

    /**
     * Default implementation of the NetworkClient interface.
     */
    public static class DefaultNetworkClient implements NetworkClient {
        @Override
        public JSONObject sendRequest(String address, int port, String request) throws IOException {
            return GETClient.sendRequest(address, port, request);
        }
    }

    /**
     * Sends an HTTP GET request and processes the response.
     * @param address The server address
     * @param port The server port
     * @param request The HTTP request string
     * @return JSONObject containing the response data, or null if the request fails
     */
    private static JSONObject sendRequest(String address, int port, String request) {
        int retries = 0;
        JSONObject result = null;
        while (true) {
            try (Socket socket = new Socket(address, port);
                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                 BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Set socket timeout to 5 seconds
                socket.setSoTimeout(5000);
                System.out.println("Connected");

                // Update and log Lamport clock
                clock.tick();
                clock.log("GETClient: sending request");
                outputStream.writeUTF(request);

                // Process the response
                String currentLine = input.readLine();
                if (currentLine != null && !currentLine.isEmpty()) {
                    String[] split = currentLine.trim().split(" ", 3);
                    int returnCode = Integer.parseInt(split[1]);
                    if (returnCode == 200) {
                        result = handleResponse(input);
                        break;
                    } else if (returnCode == 404) {
                        System.out.println("File not found");
                        return null;
                    } else {
                        System.out.println("Unknown error");
                        retries++;
                        continue;
                    }
                } else {
                    retries++;
                    continue;
                }
            } catch (IOException i) {
                System.out.println(i);
                retries++;
                if (retries > 3) {
                    System.out.println("Connection failed");
                    return null;
                }
            }
        }
        return result;
    }

    /**
     * Handles the response from the server, updating the Lamport clock and extracting JSON data.
     * @param input BufferedReader containing the server response
     * @return JSONObject with the response data
     * @throws IOException if there's an error reading the response
     */
    private static JSONObject handleResponse(BufferedReader input) throws IOException {
        JSONObject result = new JSONObject();
        String currentLine;
        // Process headers and update Lamport clock
        while ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
            if (currentLine.startsWith("Lamport-Clock:")) {
                String[] tokens = currentLine.split(":", 2);
                clock.update(Integer.parseInt(tokens[1].trim()));
                clock.log("GETClient: receive response");
                break;
            }
        }
        // Extract JSON data from response body
        while ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
            if (currentLine.startsWith("{")) {
                result = new JSONObject(currentLine);
                break;
            }
        }
        return result;
    }

    /**
     * Processes the response from the server, updating the Lamport clock and extracting JSON data.
     * @param input BufferedReader containing the server response
     * @return JSONObject with the response data
     * @throws IOException if there's an error reading the response
     */
    public static JSONObject processResponse(BufferedReader input) throws IOException {
        return handleResponse(input);
    }

    /**
     * Prints the JSON data based on the station ID.
     * @param json The JSONObject containing the response data
     * @param stationID The station ID to filter by
     */
    public static void printJson(JSONObject json, String stationID) {
        if (!stationID.equals("NULL")) {
            // Print data for a specific station
            JSONObject target = new JSONObject();
            for (String key : json.keySet()) {
                if (((JSONObject) json.get(key)).has("id") && ((String) ((JSONObject) json.get(key)).get("id")).contains(stationID)) {
                    target = (JSONObject) json.get(key);
                }
            }
            System.out.println(target.toString(4));
            return;
        }
        // Print data for all stations
        for (String key : json.keySet()) {
            System.out.println((String) json.getJSONObject(key).toString(4));
        }
    }

    /**
     * Main method to execute the GETClient.
     * @param args Command line arguments (server address, port, and optional station ID)
     */
    public static void main(String args[]) {
        if (args.length < 1) {
            System.out.println("not enough arguments...");
            return;
        }

        String address = args[0];
        int port = 4567;
        if (address.contains(":")) {
            String[] addressParts = address.split(":");
            if (addressParts.length < 3) {
                address = addressParts[0];
                port = Integer.parseInt(addressParts[1]);
            } else if (addressParts.length == 3) {
                address = addressParts[0] + ":" + addressParts[1];
                port = Integer.parseInt(addressParts[2]);
            } else {
                System.out.println("invalid address");
                return;
            }
        } else {
            System.out.println("invalid address");
            return;
        }
        String stationID = "NULL";
        if (args.length > 1) {
            stationID = args[1];
        }

        // Construct the HTTP GET request with Lamport clock
        String request = "GET /weather.json? HTTP/1.1\r\nLamport-Clock: " + clock.getValue() + "\r\n\r\n";

        try {
            GETClient client = new GETClient();
            JSONObject response = client.request(address, port, request);
            if (response == null || response.length() == 0) {
                System.out.println("failed to retrieve data");
                return;
            }
            printJson(response, stationID);
        } catch (Exception e) {
            System.out.println("failed to retrieve data");
            System.out.println(e);
            return;
        }
    }
}
