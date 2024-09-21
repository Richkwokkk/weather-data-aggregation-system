package AggregationServer;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AggregationServerTest {

    private AggregationServer server;
    private ExecutorService mockThreadPool;
    private static final String TEST_STORAGE_FILE = "test_weather_data.json";
    private Path tempFile;

    private void createTestFile() {
        try {
            Path path = Paths.get(System.getProperty("user.dir"), "test_weather_data.json").toAbsolutePath();
            System.out.println("Attempting to create test file at: " + path);
            if (!Files.exists(path)) {
                Files.write(path, "[]".getBytes());
                System.out.println("Created test file at: " + path);
            } else {
                System.out.println("Test file already exists at: " + path);
            }
        } catch (IOException e) {
            System.err.println("Error creating test file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        createTestFile();
        Path path = Paths.get(System.getProperty("user.dir"), "test_weather_data.json").toAbsolutePath();
        assertTrue(Files.exists(path), "Test file should exist after setup");
        server = new AggregationServer();
        mockThreadPool = Mockito.mock(ExecutorService.class);
        try {
            java.lang.reflect.Field field = AggregationServer.class.getDeclaredField("STORAGE_FILE");
            field.setAccessible(true);
            field.set(null, TEST_STORAGE_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        tempFile = Files.createTempFile("test_weather_data", ".json");
    }

    @AfterEach
    void tearDown() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "test_weather_data.json").toAbsolutePath();
        Files.deleteIfExists(path);
    }

    @Test
    void testHandleGetRequest() throws IOException {
        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockSocket.getOutputStream()).thenReturn(outputStream);
        
        BufferedReader mockReader = Mockito.mock(BufferedReader.class);
        when(mockReader.readLine()).thenReturn("GET /weather HTTP/1.1", "");
        
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));

        try {
            server.handleClient(mockSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String response = outputStream.toString();
        System.out.println("Response: " + response);

        assertTrue(response.contains("HTTP/1.1 200"), "Expected HTTP status 200");
        assertTrue(response.contains("Content-Type: application/json"), "Expected Content-Type application/json");
        assertTrue(response.contains("Lamport-Clock:"), "Expected Lamport-Clock header");
    }

    @Test
    void testHandlePutRequest() throws IOException {
        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockSocket.getOutputStream()).thenReturn(outputStream);
        
        String jsonData = "{\"id\":\"ABC123\",\"temperature\":\"25.5\",\"humidity\":\"60%\"}";
        BufferedReader mockReader = Mockito.mock(BufferedReader.class);
        when(mockReader.readLine())
            .thenReturn("PUT /weather HTTP/1.1", 
                        "Content-Type: application/json",
                        "Content-Length: " + jsonData.length(),
                        "",
                        jsonData,
                        null);
        
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(jsonData.getBytes()));

        server.handleClient(mockSocket);

        String response = outputStream.toString();
        assertTrue(response.contains("HTTP/1.1 201"));
        assertTrue(response.contains("Lamport-Clock:"));
        
        String storedData = new String(Files.readAllBytes(Paths.get(TEST_STORAGE_FILE)));
        assertTrue(storedData.contains("ABC123"));
        assertTrue(storedData.contains("25.5"));
        assertTrue(storedData.contains("60%"));
    }

    @Test
    void testHandleInvalidRequest() throws IOException {
        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockSocket.getOutputStream()).thenReturn(outputStream);
        
        BufferedReader mockReader = Mockito.mock(BufferedReader.class);
        when(mockReader.readLine()).thenReturn("INVALID REQUEST", "");
        
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));

        server.handleClient(mockSocket);

        String response = outputStream.toString();
        assertTrue(response.contains("HTTP/1.1 400"));
    }

    @Test
    void testDataExpiry() throws InterruptedException, IOException {
        // Add test data
        String jsonData = "[{\"id\":\"ABC123\",\"temperature\":\"25.5\",\"humidity\":\"60%\",\"lastUpdateTime\":\"" + 
                          (System.currentTimeMillis() - 40000) + "\"}]";
        Files.write(Paths.get(TEST_STORAGE_FILE), jsonData.getBytes());

        server = new AggregationServer();

        // Wait for the expiry mechanism to run
        Thread.sleep(35000);

        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockSocket.getOutputStream()).thenReturn(outputStream);
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("GET /weather HTTP/1.1\n\n".getBytes()));

        server.handleClient(mockSocket);

        String storedData = new String(Files.readAllBytes(Paths.get(TEST_STORAGE_FILE)));
        assertFalse(storedData.contains("ABC123"));
    }

    @Test
    void testConcurrentRequests() throws InterruptedException, IOException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                try {
                    Socket mockSocket = Mockito.mock(Socket.class);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    when(mockSocket.getOutputStream()).thenReturn(outputStream);

                    String jsonData = "{\"id\":\"ABC" + threadNum + "\",\"temperature\":\"25.5\",\"humidity\":\"60%\"}";
                    BufferedReader mockReader = Mockito.mock(BufferedReader.class);
                    when(mockReader.readLine())
                        .thenReturn("PUT /weather HTTP/1.1", 
                                    "Content-Type: application/json",
                                    "Content-Length: " + jsonData.length(),
                                    "",
                                    jsonData,
                                    null);

                    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(jsonData.getBytes()));

                    server.handleClient(mockSocket);
                    System.out.println("Thread " + threadNum + " processed data: " + jsonData); // Log processed data
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Read the stored data after all threads have completed
        String storedData = new String(Files.readAllBytes(tempFile));
        System.out.println("Stored Data: " + storedData); // Debugging line

        for (int i = 0; i < numThreads; i++) {
            assertTrue(storedData.contains("ABC" + i), "Data for ABC" + i + " should be present");
        }
    }
}
