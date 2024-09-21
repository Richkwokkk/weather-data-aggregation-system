import java.io.*;
import java.net.*;
import java.nio.file.*;
import org.json.JSONObject;

public class AggregationServer {
    private int portNumber = 4567;
    private static Path activeFile = Paths.get("active_data.txt");
    private static Path backupFile = Paths.get("backup_data.txt");
    private static JSONObject dataStorage = new JSONObject();
    private static JSONObject lastConnectionTime = new JSONObject();
    private LamportClock clock = new LamportClock();
    private ServerSocket serverSocket = null;

    public AggregationServer(int port) {
        this.portNumber = port;
    }

    public int validateData(String data) {
        try {
            JSONObject json = new JSONObject(data);
            return json.has("id") ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void recoverData() {
        try {
            if (Files.isReadable(activeFile) && Files.isReadable(backupFile)) {
                recoverFromBoth();
            } else if (Files.isReadable(activeFile)) {
                recoverFromActive();
            } else if (Files.isReadable(backupFile)) {
                recoverFromBackup();
            } else {
                System.out.println("No previous file");
                createFiles();
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void recoverFromBoth() throws IOException {
        long activeLastModified = Files.getLastModifiedTime(activeFile).toMillis();
        long backupLastModified = Files.getLastModifiedTime(backupFile).toMillis();
        String data;

        if (activeLastModified > backupLastModified) {
            data = new String(Files.readAllBytes(activeFile));
            if (validateData(data) == 1) {
                Files.copy(activeFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                recoverFromBackup();
            }
        } else {
            data = new String(Files.readAllBytes(backupFile));
            if (validateData(data) == 1) {
                Files.copy(backupFile, activeFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                recoverFromActive();
            }
        }
        dataStorage = new JSONObject(data);
    }

    private void recoverFromActive() throws IOException {
        String data = new String(Files.readAllBytes(activeFile));
        if (validateData(data) == 1) {
            Files.copy(activeFile, backupFile);
            dataStorage = new JSONObject(data);
        } else {
            Files.delete(activeFile);
        }
    }

    private void recoverFromBackup() throws IOException {
        String data = new String(Files.readAllBytes(backupFile));
        if (validateData(data) == 1) {
            Files.copy(backupFile, activeFile);
            dataStorage = new JSONObject(data);
        } else {
            Files.delete(backupFile);
        }
    }

    private void createFiles() {
        try {
            Files.createFile(activeFile);
            Files.createFile(backupFile);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public synchronized void store(JSONObject data) {
        synchronized (dataStorage) {
            dataStorage.put(data.getString("id"), data);
            synchronized (lastConnectionTime) {
                lastConnectionTime.put(data.getString("id"), System.currentTimeMillis());
            }
        }
    }

    public synchronized void backup() {
        // copy the content of activeFile to backupFile
        try {
            Files.copy(activeFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    public synchronized void saveData() {
        try {
            Files.write(activeFile, AggregationServer.dataStorage.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    public void startServer() throws IOException {
        serverSocket = new ServerSocket(portNumber);
        System.out.println("Server started on port: " + portNumber);

        while (true) {
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
                System.out.println("A new client is connected: " + clientSocket);
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter output = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                System.out.println("Assigning new thread for this client");
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

    private static void janitor() {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long currentTime = System.currentTimeMillis();

            synchronized (dataStorage) {
                for (String key : lastConnectionTime.keySet()) {
                    if (currentTime - lastConnectionTime.getLong(key) > 30000) {
                        dataStorage.remove(key);
                        synchronized (lastConnectionTime) {
                            lastConnectionTime.remove(key);
                        }
                    }
                }
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private AggregationServer server;
        private BufferedReader input;
        private BufferedWriter output;

        public ClientHandler(Socket clientSocket, BufferedReader input, BufferedWriter output, AggregationServer server) {
            this.socket = clientSocket;
            this.server = server;
            this.input = input;
            this.output = output;
        }

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

        private void handlePutRequest(BufferedReader input, BufferedWriter output) throws IOException {
            int code = 204;
            JSONObject currentData = new JSONObject();
            String currentLine;
            while ((currentLine = input.readLine()) != null && !currentLine.isEmpty()) {
                if (currentLine.startsWith("Lamport-Clock:")) {
                    String[] tokens = currentLine.split(":", 2);
                    server.clock.update(Integer.parseInt(tokens[1].trim()));
                    server.clock.log("Agg: receive PUT");
                    break;
                }
            }
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

        private void sendBadRequestResponse(BufferedWriter output) throws IOException {
            server.clock.tick();
            server.clock.log("Agg: send 400 response");
            output.write("HTTP/1.1 400 Bad Request\r\n");
            output.write("\r\n");
            output.flush();
        }
    }

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
}