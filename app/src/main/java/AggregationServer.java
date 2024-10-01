import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.HashSet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AggregationServer class handles the server-side operations for aggregating and managing data.
 */
public class AggregationServer {
    private int portNumber = 4567;
    private static Path activeFile = Paths.get("active_data.txt");
    private static Path backupFile = Paths.get("backup_data.txt");
    private static JSONObject dataStorage = new JSONObject();
    private static JSONObject lastConnectionTime = new JSONObject();
    private LamportClock clock = new LamportClock();
    private ServerSocket serverSocket = null;
    private Thread janitorThread;

    /**
     * Constructor for AggregationServer.
     * @param port The port number on which the server will listen.
     */
    public AggregationServer(int port) {
        this.portNumber = port;
        AggregationServer.dataStorage = new JSONObject();
        // Initialize and start the janitor thread
        this.janitorThread = new Thread(() -> {
            janitor();
        });
        this.janitorThread.setDaemon(true);
        this.janitorThread.start();
    }

    /**
     * Validates the incoming data.
     * @param data The data to be validated.
     * @return 1 if data is valid, 0 otherwise.
     */
    public int validateData(String data) {
        if (data == null || data.trim().isEmpty()) {
            System.out.println("Data is empty or null.");
            return 0;
        }
        try {
            // Attempt to parse the data as JSON
            JSONObject jsonObject = new JSONObject(data);
            // Check if the JSON object has any keys
            return jsonObject.length() > 0 ? 1 : 0;
        } catch (Exception e) {
            System.out.println("Error validating data: " + e);
            return 0;
        }
    }

    /**
     * Recovers data from active and backup files.
     */
    public void recoverData() {
        try {
            System.out.println("Recovering data...");
            // Check the availability of active and backup files
            if (Files.isReadable(activeFile) && Files.isReadable(backupFile)) {
                recoverFromBoth();
            } else if (Files.isReadable(activeFile)) {
                recoverFromActive();
            } else if (Files.isReadable(backupFile)) {
                recoverFromBackup();
            } else {
                // If no previous files are found, create new files and reset dataStorage
                System.out.println("No previous files found. Creating new files and resetting dataStorage.");
                createFiles();
                dataStorage = new JSONObject();
            }
            System.out.println("Data recovery complete. DataStorage: " + dataStorage.toString());
        } catch (IOException e) {
            System.out.println("Error during data recovery: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Recovers data from both active and backup files.
     * @throws IOException If an I/O error occurs.
     */
    private void recoverFromBoth() throws IOException {
        long activeLastModified = Files.getLastModifiedTime(activeFile).toMillis();
        long backupLastModified = Files.getLastModifiedTime(backupFile).toMillis();
        String data;

        if (activeLastModified > backupLastModified) {
            data = new String(Files.readAllBytes(activeFile));
        } else {
            data = new String(Files.readAllBytes(backupFile));
        }

        if (data.trim().isEmpty()) {
            System.out.println("Data file is empty. Starting with empty dataStorage.");
            dataStorage = new JSONObject();
        } else {
            try {
                dataStorage = new JSONObject(data);
            } catch (JSONException e) {
                System.out.println("Invalid JSON data in file. Starting with empty dataStorage.");
                dataStorage = new JSONObject();
            }
        }
    }

    /**
     * Recovers data from the active file.
     * @throws IOException If an I/O error occurs.
     */
    private void recoverFromActive() throws IOException {
        System.out.println("Recovering from active file...");
        String data = new String(Files.readAllBytes(activeFile));
        System.out.println("Active file content: " + data);
        if (data.trim().isEmpty()) {
            System.out.println("Active file is empty. Starting with empty dataStorage.");
            dataStorage = new JSONObject();
        } else {
            try {
                dataStorage = new JSONObject(data);
                System.out.println("Data validated and stored. DataStorage: " + dataStorage.toString());
                Files.copy(activeFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (JSONException e) {
                System.out.println("Invalid JSON data in active file. Starting with empty dataStorage.");
                dataStorage = new JSONObject();
                Files.delete(activeFile);
            }
        }
    }

    /**
     * Recovers data from the backup file.
     * @throws IOException If an I/O error occurs.
     */
    private void recoverFromBackup() throws IOException {
        System.out.println("Recovering from backup file...");
        String data = new String(Files.readAllBytes(backupFile));
        System.out.println("Backup file content: " + data);
        if (data.trim().isEmpty()) {
            System.out.println("Backup file is empty. Starting with empty dataStorage.");
            dataStorage = new JSONObject();
        } else {
            try {
                dataStorage = new JSONObject(data);
                System.out.println("Data validated and stored. DataStorage: " + dataStorage.toString());
                Files.copy(backupFile, activeFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (JSONException e) {
                System.out.println("Invalid JSON data in backup file. Starting with empty dataStorage.");
                dataStorage = new JSONObject();
                Files.delete(backupFile);
            }
        }
    }

    /**
     * Creates the active and backup files.
     */
    private void createFiles() {
        try {
            Files.createFile(activeFile);
            Files.createFile(backupFile);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    /**
     * Stores the provided JSON data.
     * @param jsonData The JSON data to be stored.
     */
    public synchronized void store(JSONObject jsonData) {
        if (jsonData.has("id") && !jsonData.getString("id").isEmpty()) {
            String id = jsonData.getString("id");
            dataStorage.put(id, jsonData);
            lastConnectionTime.put(id, System.currentTimeMillis());
        }
    }

    /**
     * Creates a backup of the active file.
     */
    public synchronized void backup() {
        try {
            Files.copy(activeFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    /**
     * Saves the current data to the active file.
     */
    public synchronized void saveData() {
        try {
            Files.write(activeFile, AggregationServer.dataStorage.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    /**
     * Starts the server and listens for client connections.
     * @throws IOException If an I/O error occurs when opening the server socket.
     */
    public void startServer() throws IOException {
        serverSocket = new ServerSocket(portNumber);
        System.out.println("Server started on port: " + portNumber);

        while (true) {
            Socket clientSocket = null;
            try {
                // Accept a new client connection
                clientSocket = serverSocket.accept();
                System.out.println("A new client is connected: " + clientSocket);

                // Create input and output streams for the client
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter output = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                System.out.println("Assigning new thread for this client");
                // Create and start a new thread to handle the client
                Thread clientThread = new ClientHandler(clientSocket, input, output, this);
                clientThread.start();
            } catch (Exception e) {
                if (clientSocket != null) {
                    clientSocket.close();
                }
                e.printStackTrace();
            }
        }
    }

    /**
     * Janitor method to remove stale data.
     */
    private static void janitor() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Sleep for 1 second between checks
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long currentTime = System.currentTimeMillis();

            synchronized (dataStorage) {
                // Iterate through all entries and remove those older than 30 seconds
                for (String key : new HashSet<>(lastConnectionTime.keySet())) {
                    if (currentTime - lastConnectionTime.getLong(key) > 30000) {
                        dataStorage.remove(key);
                        lastConnectionTime.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Inner class to handle client connections.
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private AggregationServer server;
        private BufferedReader input;
        private BufferedWriter output;

        /**
         * Constructor for ClientHandler.
         * @param clientSocket The socket for the client connection.
         * @param input The input stream from the client.
         * @param output The output stream to the client.
         * @param server The AggregationServer instance.
         */
        public ClientHandler(Socket clientSocket, BufferedReader input, BufferedWriter output, AggregationServer server) {
            this.socket = clientSocket;
            this.server = server;
            this.input = input;
            this.output = output;
        }

        /**
         * Main run method for handling client requests.
         */
        @Override
        public void run() {
            try {
                String currentLine;
                if ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
                    String[] split = currentLine.trim().split(" ", 3);
                    String method = split[0];
                    if (method.matches(".*GET.*")) {
                        handleGetRequest(input, output);
                    } else if (method.matches(".*PUT.*")) {
                        handlePutRequest(input, output);
                    } else {
                        sendBadRequestResponse(output);
                    }
                } else {
                    sendBadRequestResponse(output);
                }
            } catch (IOException i) {
                System.out.println(i);
            }
        }

        /**
         * Handles GET requests.
         * @param input The input stream from the client.
         * @param output The output stream to the client.
         * @throws IOException If an I/O error occurs.
         */
        private void handleGetRequest(BufferedReader input, BufferedWriter output) throws IOException {
            JSONObject result = AggregationServer.dataStorage;
            server.clock.tick();
            server.clock.log("Agg: send GET response");
            output.write("HTTP/1.1 200 OK\r\n");
            output.write("Content-Type: application/json\r\n");
            output.write("Content-Length: " + result.toString().length() + "\r\n");
            output.write("Lamport-Clock: " + server.clock.getValue() + "\r\n");
            output.write(result.toString());
            output.write("\r\n");
            output.flush();
            socket.close();
        }

        /**
         * Handles PUT requests.
         * @param input The input stream from the client.
         * @param output The output stream to the client.
         * @throws IOException If an I/O error occurs.
         */
        private void handlePutRequest(BufferedReader input, BufferedWriter output) throws IOException {
            int code = 204;
            JSONObject currentData = new JSONObject();
            String currentLine;
            // Read the PUT request and update the Lamport clock
            while ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
                if (currentLine.startsWith("Lamport-Clock:")) {
                    String[] tokens = currentLine.split(":", 2);
                    server.clock.update(Integer.parseInt(tokens[1].trim()));
                    server.clock.log("Agg: receive PUT");
                    break;
                }
            }
            // Read the JSON data from the PUT request
            while ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
                if (currentLine.startsWith("{")) {
                    code = 200;
                    currentData = new JSONObject(currentLine);
                    if (currentData.length() > 1) {
                        if (!AggregationServer.dataStorage.has(currentData.getString("id"))) {
                            code = 201;
                        }
                        server.store(currentData);
                        break;
                    }
                }
            }
            // Send the PUT response to the client
            server.clock.tick();
            server.clock.log("Agg: send PUT response");
            output.write("HTTP/1.1 " + code + " OK\r\n");
            output.write("Content-Type: application/json\r\n");
            output.write("Content-Length: " + currentData.toString().length() + "\r\n");
            output.write("Lamport-Clock: " + server.clock.getValue() + "\r\n");
            output.write("\r\n");
            output.write(currentData.toString());
            output.write("\r\n");
            output.flush();
            server.saveData();
            server.backup();
        }

        /**
         * Sends a bad request response to the client.
         * @param output The output stream to the client.
         * @throws IOException If an I/O error occurs.
         */
        private void sendBadRequestResponse(BufferedWriter output) throws IOException {
            server.clock.tick();
            server.clock.log("Agg: send 400 response");
            output.write("HTTP/1.1 400 Bad Request\r\n");
            output.write("\r\n");
            output.flush();
        }
    }

    /**
     * Main method to start the AggregationServer.
     * @param args Command line arguments. The first argument can be used to specify the port number.
     */
    public static void main(String args[]) {
        int port = 4567;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        AggregationServer server = new AggregationServer(port);
        server.recoverData();
        Thread janitorThread = new Thread(() -> janitor());
        janitorThread.start();
        try {
            server.startServer();
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    /**
     * Gets the current data storage.
     * @return The current data storage.
     */
    public JSONObject getDataStorage() {
        return dataStorage;
    }

    /**
     * Clears the data storage.
     */
    public void clearDataStorage() {
        dataStorage = new JSONObject();
        lastConnectionTime = new JSONObject();
    }

    /**
     * Gets the active file path.
     * @return The active file path.
     */
    public static Path getActiveFile() {
        return activeFile;
    }

    /**
     * Gets the backup file path.
     * @return The backup file path.
     */
    public static Path getBackupFile() {
        return backupFile;
    }
    
}