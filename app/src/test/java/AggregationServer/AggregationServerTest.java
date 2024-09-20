package AggregationServer;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AggregationServerTest {

    private AggregationServer server;
    private ExecutorService mockThreadPool;
    private static final String TEST_STORAGE_FILE = "test_weather_data.json";

    @BeforeEach
    void setUp() {
        server = new AggregationServer();
        mockThreadPool = Mockito.mock(ExecutorService.class);
        try {
            java.lang.reflect.Field field = AggregationServer.class.getDeclaredField("STORAGE_FILE");
            field.setAccessible(true);
            field.set(null, TEST_STORAGE_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_STORAGE_FILE));
    }

    @Test
    void testHandleGetRequest() throws IOException {
        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(mockSocket.getOutputStream()).thenReturn(outputStream);
        
        BufferedReader mockReader = Mockito.mock(BufferedReader.class);
        when(mockReader.readLine()).thenReturn("GET /weather HTTP/1.1", "");
        
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));

        server.handleClient(mockSocket);

        String response = outputStream.toString();
        assertTrue(response.contains("HTTP/1.1 200"));
        assertTrue(response.contains("Content-Type: application/json"));
        assertTrue(response.contains("Lamport-Clock:"));
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        String storedData = new String(Files.readAllBytes(Paths.get(TEST_STORAGE_FILE)));
        for (int i = 0; i < numThreads; i++) {
            assertTrue(storedData.contains("ABC" + i));
        }
    }
}
