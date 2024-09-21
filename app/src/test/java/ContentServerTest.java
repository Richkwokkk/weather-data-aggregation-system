import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import org.json.JSONObject;
import org.mockito.Mockito;

class ContentServerTest {
    private static ContentServer contentServer;
    private static final String TEST_ADDRESS = "localhost:8080";
    private static final String TEST_FILE_PATH = "test_weather_data.txt";

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        createTestFile();
        ContentServer server = new ContentServer(TEST_ADDRESS, 8080, TEST_FILE_PATH);
        
        // Start the server in a separate thread
        new Thread(() -> {
            try {
                contentServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        
        // Give the server some time to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        // Stop the server
        contentServer.stop();
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
    }

    private static void createTestFile() throws IOException {
        String testData = 
            "id:IDS60901\n" +
            "name:Adelaide (West Terrace /  ngayirdapira)\n" +
            "state: SA\n" +
            "time_zone:CST\n" +
            "lat:-34.9\n" +
            "lon:138.6\n" +
            "local_date_time:15/04:00pm\n" +
            "local_date_time_full:20230715160000\n" +
            "air_temp:13.3\n" +
            "apparent_t:9.5\n" +
            "cloud:Partly cloudy\n" +
            "dewpt:5.7\n" +
            "press:1023.9\n" +
            "rel_hum:60\n" +
            "wind_dir:S\n" +
            "wind_spd_kmh:15\n" +
            "wind_spd_kt:8";
        Files.write(Paths.get(TEST_FILE_PATH), testData.getBytes());
    }

    @Test
    void testFileReading() {
        ArrayList<JSONObject> result = contentServer.readFile(TEST_FILE_PATH);
        assertNotNull(result);
        assertEquals(1, result.size());
        JSONObject json = result.get(0);
        assertEquals("IDS60901", json.getString("id"));
        assertEquals("Adelaide (West Terrace /  ngayirdapira)", json.getString("name"));
        assertEquals("SA", json.getString("state"));
        assertEquals("13.3", json.getString("air_temp"));
    }

    @Test
    void testJSONFormatting() {
        ArrayList<JSONObject> data = contentServer.readFile(TEST_FILE_PATH);
        JSONObject json = data.get(0);
        String formattedJson = json.toString();
        assertTrue(formattedJson.startsWith("{"));
        assertTrue(formattedJson.endsWith("}"));
        assertTrue(formattedJson.contains("\"id\":\"IDS60901\""));
        assertTrue(formattedJson.contains("\"wind_spd_kt\":\"8\""));
    }

    @Test
    void testPUTRequestFormatting() throws IOException {
        // Mock the socket to capture the output
        Socket mockSocket = Mockito.mock(Socket.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(mockSocket.getOutputStream()).thenReturn(outputStream);
        Mockito.when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("HTTP/1.1 200 OK\r\n\r\n".getBytes()));
        
        // Use reflection to set the mocked socket
        Field socketField = ContentServer.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        socketField.set(contentServer, mockSocket);
        
        contentServer.sendJsons(contentServer.readFile(TEST_FILE_PATH));
        
        String sentData = outputStream.toString();
        assertTrue(sentData.startsWith("PUT /weather.json HTTP/1.1\r\n"));
        assertTrue(sentData.contains("Content-Type: application/json\r\n"));
        assertTrue(sentData.contains("Content-Length: "));
        assertTrue(sentData.contains("Lamport-Clock: "));
        assertTrue(sentData.contains("\"id\":\"IDS60901\""));
    }

    @Test
    void testLamportClockUpdate() throws IOException {
        // Get initial clock value
        Field clockField = ContentServer.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        LamportClock clock = (LamportClock) clockField.get(contentServer);
        int initialValue = clock.getValue();
        
        // Mock the socket to simulate server response
        Socket mockSocket = Mockito.mock(Socket.class);
        Mockito.when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        Mockito.when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(
            ("HTTP/1.1 200 OK\r\n" +
             "Lamport-Clock: " + (initialValue + 5) + "\r\n\r\n").getBytes()
        ));
        
        // Use reflection to set the mocked socket
        Field socketField = ContentServer.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        socketField.set(contentServer, mockSocket);
        
        contentServer.sendJsons(contentServer.readFile(TEST_FILE_PATH));
        
        assertTrue(clock.getValue() > initialValue);
    }

    @Test
    void testRetryMechanism() throws IOException {
        // Mock the socket to simulate connection failure
        Socket mockSocket = Mockito.mock(Socket.class);
        Mockito.when(mockSocket.getOutputStream()).thenThrow(new IOException("Connection failed"));
        
        // Use reflection to set the mocked socket
        Field socketField = ContentServer.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        socketField.set(contentServer, mockSocket);
        
        assertThrows(IOException.class, () -> {
            contentServer.sendJsons(contentServer.readFile(TEST_FILE_PATH));
        });
        
        // Verify that the method attempted to send data multiple times
        Mockito.verify(mockSocket, Mockito.times(3)).getOutputStream();
    }
}