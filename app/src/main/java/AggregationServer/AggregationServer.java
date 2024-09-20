package AggregationServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import LamportClock.LamportClock;
import JSONHandler.JSONHandler;

public class AggregationServer {
    private static final String STORAGE_FILE = "weather_data.json";
    private static final long EXPIRY_TIME = 30000; // 30 seconds
    private final Map<String, Map<String, String>> weatherData = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private final LamportClock lamportClock = new LamportClock();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void start(int port) throws IOException {
        loadDataFromFile();
        startExpiryChecker();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server started on port " + port);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String requestLine = in.readLine();
            if (requestLine != null) {
                String[] requestParts = requestLine.split(" ");
                String method = requestParts[0];
                String path = requestParts[1];

                if ("GET".equals(method)) {
                    handleGetRequest(out);
                } else if ("PUT".equals(method)) {
                    handlePutRequest(in, out);
                } else {
                    sendResponse(out, 400, "Bad Request");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleGetRequest(PrintWriter out) {
        rwLock.readLock().lock();
        try {
            lamportClock.tick();
            List<Map<String, String>> currentData = new ArrayList<>(weatherData.values());
            currentData.sort((a, b) -> Long.compare(
                Long.parseLong(b.getOrDefault("timestamp", "0")),
                Long.parseLong(a.getOrDefault("timestamp", "0"))
            ));
            String jsonResponse = JSONHandler.convertToJSON(currentData);
            sendResponse(out, 200, jsonResponse);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            content.append(line);
        }

        List<Map<String, String>> dataList = JSONHandler.parseJSON(content.toString());
        if (!dataList.isEmpty()) {
            Map<String, String> data = dataList.get(0);
            rwLock.writeLock().lock();
            try {
                lamportClock.update(Long.parseLong(data.getOrDefault("timestamp", "0")));
                lamportClock.tick();
                data.put("timestamp", String.valueOf(lamportClock.getTime()));
                data.put("lastUpdateTime", String.valueOf(System.currentTimeMillis()));
                String id = data.get("id");
                boolean isNewEntry = !weatherData.containsKey(id);
                weatherData.put(id, data);
                saveDataToFile();
                sendResponse(out, isNewEntry ? 201 : 200, "Data updated successfully");
            } finally {
                rwLock.writeLock().unlock();
            }
        } else {
            sendResponse(out, 204, "No Content");
        }
    }

    private void sendResponse(PrintWriter out, int statusCode, String body) {
        out.println("HTTP/1.1 " + statusCode);
        out.println("Content-Type: application/json");
        out.println("Lamport-Clock: " + lamportClock.getTime());
        out.println();
        out.println(body);
    }

    private void loadDataFromFile() {
        rwLock.writeLock().lock();
        try {
            Path path = Paths.get(STORAGE_FILE);
            if (Files.exists(path)) {
                String content = new String(Files.readAllBytes(path));
                List<Map<String, String>> dataList = JSONHandler.parseJSON(content);
                for (Map<String, String> data : dataList) {
                    weatherData.put(data.get("id"), data);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void saveDataToFile() {
        rwLock.readLock().lock();
        try {
            List<Map<String, String>> dataList = new ArrayList<>(weatherData.values());
            String jsonContent = JSONHandler.convertToJSON(dataList);
            Files.write(Paths.get(STORAGE_FILE), jsonContent.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void startExpiryChecker() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::removeExpiredData, EXPIRY_TIME, EXPIRY_TIME, TimeUnit.MILLISECONDS);
    }

    private void removeExpiredData() {
        rwLock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            boolean dataRemoved = weatherData.entrySet().removeIf(entry -> {
                long lastUpdateTime = Long.parseLong(entry.getValue().getOrDefault("lastUpdateTime", "0"));
                return currentTime - lastUpdateTime > EXPIRY_TIME;
            });
            if (dataRemoved) {
                saveDataToFile();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4567;
        try {
            new AggregationServer().start(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}