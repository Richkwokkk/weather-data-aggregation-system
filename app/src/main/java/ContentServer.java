import java.io.*;
import java.net.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ContentServer class for handling weather data transmission to multiple servers.
 */
public class ContentServer {
    private static LamportClock clock = new LamportClock();
    private Socket socket;
    private BufferedReader myBufferedReader;
    private DataOutputStream myOutputStream;
    private JSONObject myJSONObject;
    private String filePath;
    private String address;
    private int port = 4567;
    private boolean testMode = false;
    private static final CopyOnWriteArrayList<ContentServer> serverInstances = new CopyOnWriteArrayList<>();
    private static final int MAX_SERVERS = 3;

    /**
     * Reads JSON data from a file and returns it as a list of strings.
     * @return List of JSON strings read from the file
     * @throws IOException if there's an error reading the file or if the file format is invalid
     */
    private List<String> readFile() throws IOException {
        try {
            JSONObject currentData = new JSONObject();
            List<JSONObject> resultList = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip empty lines and brackets
                    line = line.trim();
                    if (line.isEmpty() || line.equals("{") || line.equals("}")) continue;

                    // Remove trailing comma if present
                    if (line.endsWith(",")) {
                        line = line.substring(0, line.length() - 1);
                    }

                    // Split the line into key and value
                    String[] tokens = line.split(":", 2);
                    if (tokens.length < 2) {
                        continue;
                    }

                    String key = tokens[0].replaceAll("\"", "").trim();
                    String value = tokens[1].replaceAll("\"", "").trim();

                    // Start a new JSON object when a new ID is encountered
                    if (key.equalsIgnoreCase("id") && !currentData.isEmpty()) {
                        resultList.add(new JSONObject(currentData.toString()));
                        currentData = new JSONObject();
                    }

                    currentData.put(key, value);
                }

                // Add the last JSON object if not empty
                if (!currentData.isEmpty()) {
                    resultList.add(new JSONObject(currentData.toString()));
                }

                // Convert JSONObjects to strings
                return resultList.stream()
                    .map(JSONObject::toString)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new IOException("Invalid file format", e);
        }
    }

    /**
     * Public method to read file content from a given file path.
     * @param filePath Path to the file to be read
     * @return List of JSON strings read from the file
     * @throws IOException if there's an error reading the file
     */
    public List<String> readFilePublic(String filePath) throws IOException {
        this.filePath = filePath;
        return readFile();
    }

    /**
     * Sends a list of JSON objects to the server.
     * @param jsonList List of JSON objects to be sent
     * @throws IOException if there's an error in sending data or connecting to the server
     */
    public void sendJsons(ArrayList<JSONObject> jsonList) throws IOException {
        if (jsonList == null || jsonList.isEmpty()) {
            System.out.println("No valid JSON data to send.");
            return;
        }

        int length = jsonList.size();
        int retries = 0;

        for (int i = 0; i < length; i++) {
            try (Socket socket = new Socket(address, port)) {
                // Set socket timeout to 5 seconds
                socket.setSoTimeout(5000);
                System.out.println("Connected");

                JSONObject currentJSON = jsonList.get(i);
                clock.tick();
                clock.log("ContentServer: sending PUT");

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    // Skip entries with invalid IDs
                    if (currentJSON.getString("id").length() < 2) {
                        System.out.println("Invalid entry, id is empty");
                        continue;
                    }

                    // Construct the PUT request with Lamport clock
                    String request = "PUT /weather.json HTTP/1.1\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: " + currentJSON.toString().length() + "\r\n" +
                            "Lamport-Clock: " + clock.getValue() + "\r\n";
                    writer.write(request);
                    writer.write(currentJSON.toString());
                    writer.write("\r\n");
                    writer.flush();

                    // Wait for 1 second before reading response
                    Thread.sleep(1000);

                    // Read the response from the server
                    String ret = reader.readLine();
                    String[] split = ret.trim().split(" ", 3);
                    int returnCode = Integer.parseInt(split[1]);

                    // Update Lamport clock based on server response
                    while ((ret = reader.readLine()) != null && !ret.isEmpty()) {
                        if (ret.startsWith("Lamport-Clock:")) {
                            String[] tokens = ret.split(":", 2);
                            clock.update(Integer.parseInt(tokens[1].trim()));
                            clock.log("ContentServer: receive response");
                        }
                    }

                    // Retry if the server didn't respond with 200 or 201
                    if (returnCode != 200 && returnCode != 201) {
                        i--;
                        Thread.sleep(1000);
                    }
                } catch (IOException | InterruptedException e) {
                    // Handle exceptions and retry if necessary
                    i--;
                    retries++;
                    if (retries > 3) {
                        System.out.println("Connection failed after 3 retries");
                        throw new IOException("Connection failed after 3 retries", e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException e) {
                // Handle connection failures
                System.out.println("Connection failed: " + e.getMessage());
                if (testMode) {
                    System.out.println("Test mode: Continuing despite connection failure");
                    continue;
                }
                retries++;
                if (retries > 3) {
                    throw new IOException("Connection failed after 3 retries", e);
                }
                i--;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Sets the socket for the server.
     * @param socket The socket to be set
     */ 
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * Constructor for ContentServer.
     * @param address The address of the server
     * @param port The port number for the server
     * @param filePath The path to the file containing weather data
     */
    public ContentServer(String address, int port, String filePath) {
        this.address = address;
        this.port = port;
        this.filePath = filePath;
    }

    /**
     * Sets the test mode for the server.
     * @param testMode The test mode flag
     */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /**
     * Sends data read from the file to the server.
     * @throws IOException if there's an error reading the file or sending data
     */
    public void sendData() throws IOException {
        List<String> jsonStrings = readFile();
        if (jsonStrings != null && !jsonStrings.isEmpty()) {
            ArrayList<JSONObject> jsonList = new ArrayList<>();
            for (String jsonString : jsonStrings) {
                try {
                    jsonList.add(new JSONObject(jsonString));
                } catch (Exception e) {
                    throw new IOException("Invalid JSON data in file", e);
                }
            }
            sendJsons(jsonList);
        } else {
            throw new IOException("No valid JSON data read from file.");
        }
    }

    /**
     * Adds a new ContentServer instance to the list of server instances.
     * @param server ContentServer instance to be added
     */
    public static void addServerInstance(ContentServer server) {
        if (serverInstances.size() < MAX_SERVERS) {
            serverInstances.add(server);
        }
    }

    /**
     * Removes a ContentServer instance from the list of server instances.
     * @param server ContentServer instance to be removed
     */
    public static void removeServerInstance(ContentServer server) {
        serverInstances.remove(server);
    }

    /**
     * Sends data to all registered server instances.
     * @param jsonList List of JSON objects to be sent to all servers
     * @throws IOException if there's an error sending data to all servers
     */
    public static void sendDataToAllServers(List<JSONObject> jsonList) throws IOException {
        int failedServers = 0;
        IOException lastException = null;

        for (ContentServer server : serverInstances) {
            try {
                server.sendJsons(new ArrayList<>(jsonList));
            } catch (IOException e) {
                System.err.println("Failed to send data to server: " + e.getMessage());
                failedServers++;
                lastException = e;
            }
        }
        
        if (failedServers == serverInstances.size() && !serverInstances.isEmpty()) {
            throw new IOException("All servers failed to process the request", lastException);
        } else if (failedServers > 0) {
            System.err.println("Failed to send data to " + failedServers + " server(s)");
        }
    }

    /**
     * Main method to run the ContentServer application.
     * @param args Command-line arguments: [address] [filepath]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Not enough arguments...");
            return;
        }

        String address = args[0];
        String filepath = args[1];
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
                System.out.println("Invalid address");
                return;
            }
        } else {
            System.out.println("Invalid address");
            return;
        }

        for (int i = 0; i < MAX_SERVERS; i++) {
            ContentServer server = new ContentServer(address, port + i, filepath);
            addServerInstance(server);
        }

        try {
            List<String> jsonStrings = serverInstances.get(0).readFile();
            List<JSONObject> jsonList = jsonStrings.stream()
                .map(JSONObject::new)
                .collect(Collectors.toList());
            sendDataToAllServers(jsonList);
        } catch (IOException e) {
            System.err.println("Error processing data: " + e.getMessage());
        }
    }

    /**
     * Clears all registered server instances.
     */
    public static void clearServerInstances() {
        serverInstances.clear();
    }

    /**
     * Gets the count of registered server instances.
     * @return Number of registered server instances
     */
    public static int getServerInstancesCount() {
        return serverInstances.size();
    }

}
