import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import org.json.JSONObject;

class AggregationServerTest {
    private AggregationServer server;
    private final int TEST_PORT = 4568;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        server = new AggregationServer(TEST_PORT);
        new Thread(() -> {
            try {
                server.startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get("active_data.txt"));
        Files.deleteIfExists(Paths.get("backup_data.txt"));
    }

    private String sendRequest(String method, String data) throws IOException {
        Socket socket = new Socket("localhost", TEST_PORT);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.write(method + " /weather.json HTTP/1.1\r\n");
        if (data != null) {
            out.write("Content-Type: application/json\r\n");
            out.write("Content-Length: " + data.length() + "\r\n");
            out.write("\r\n");
            out.write(data);
        } else {
            out.write("\r\n");
        }
        out.flush();

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            response.append(line).append("\n");
        }

        socket.close();
        return response.toString();
    }

    @Test
    void testFirstDataUpload() throws IOException {
        String putData = "{\"id\":\"TEST001\",\"name\":\"Test Station\"}";
        String response = sendRequest("PUT", putData);
        assertTrue(response.contains("HTTP/1.1 201 OK"));
    }

    @Test
    void testSubsequentDataUpload() throws IOException {
        String putData = "{\"id\":\"TEST001\",\"name\":\"Test Station\"}";
        sendRequest("PUT", putData); // First upload
        String response = sendRequest("PUT", putData);
        assertTrue(response.contains("HTTP/1.1 200 OK"));
    }

    @Test
    void testInvalidMethod() throws IOException {
        String response = sendRequest("POST", null);
        assertTrue(response.contains("HTTP/1.1 400 Bad Request"));
    }

    @Test
    void testEmptyContent() throws IOException {
        String response = sendRequest("PUT", "");
        assertTrue(response.contains("HTTP/1.1 204 No Content"));
    }

    @Test
    void testInvalidJSON() throws IOException {
        String invalidData = "{\"id\":\"TEST001\",\"name\":\"Test Station\"";
        String response = sendRequest("PUT", invalidData);
        assertTrue(response.contains("HTTP/1.1 500 Internal Server Error"));
    }

    @Test
    void testGetRequest() throws IOException {
        String putData = "{\"id\":\"TEST001\",\"name\":\"Test Station\"}";
        sendRequest("PUT", putData);
        String response = sendRequest("GET", null);
        assertTrue(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("TEST001"));
        assertTrue(response.contains("Test Station"));
    }

    @Test
    void testDataPersistence() throws IOException {
        String putData = "{\"id\":\"TEST001\",\"name\":\"Test Station\"}";
        sendRequest("PUT", putData);
        
        // Restart the server
        server = new AggregationServer(TEST_PORT);
        new Thread(() -> {
            try {
                server.startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String response = sendRequest("GET", null);
        assertTrue(response.contains("TEST001"));
        assertTrue(response.contains("Test Station"));
    }

    @Test
    void testConcurrentRequests() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                try {
                    String putData = "{\"id\":\"TEST" + String.format("%03d", id) + "\",\"name\":\"Test Station " + id + "\"}";
                    String response = sendRequest("PUT", putData);
                    assertTrue(response.contains("OK"));
                } catch (IOException e) {
                    fail("Exception occurred: " + e.getMessage());
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        
        try {
            String response = sendRequest("GET", null);
            for (int i = 0; i < numThreads; i++) {
                assertTrue(response.contains("TEST" + String.format("%03d", i)));
            }
        } catch (IOException e) {
            fail("Exception occurred: " + e.getMessage());
        }
    }
}