import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test class for ContentServer functionality.
 * This class tests various aspects of the ContentServer, including file reading,
 * JSON parsing, server instance management, and data sending operations.
 */
public class ContentServerTest {
    private ContentServer contentServer;
    private String testAddress = "localhost";
    private int testPort = 8080;
    private String testFilePath = "test_weather.json";
    private String invalidFilePath = "invalid_test_weather.json";

    /**
     * Set up the test environment before each test.
     * This method creates a test file and initializes the ContentServer.
     */
    @BeforeEach
    public void setUp() throws IOException {
        // Initialize ContentServer with test parameters
        contentServer = new ContentServer(testAddress, testPort, testFilePath);
        contentServer.setTestMode(true);

        // Delete existing test file if it exists
        Files.deleteIfExists(Paths.get(testFilePath));

        // Create a new test file with sample JSON content
        Path testFile = Files.createFile(Paths.get(testFilePath));
        String jsonContent = "{\n" +
            "  \"id\": \"IDS60901\",\n" +
            "  \"name\": \"Adelaide (West Terrace / ngayirdapira)\",\n" +
            "  \"state\": \"SA\",\n" +
            "  \"time_zone\": \"CST\",\n" +
            "  \"air_temp\": 13.3,\n" +
            "  \"cloud\": \"Partly cloudy\",\n" +
            "  \"wind_spd_kmh\": 15,\n" +
            "  \"wind_dir\": \"S\"\n" +
            "}";
        Files.write(testFile, jsonContent.getBytes());

        // Clear any existing server instances
        ContentServer.clearServerInstances();
    }

    /**
     * Clean up the test environment after each test.
     * This method deletes test files and clears server instances.
     */
    @AfterEach
    void cleanup() {
        // Delete test files
        new File(invalidFilePath).delete();
        new File(testFilePath).delete();

        // Clear server instances
        ContentServer.clearServerInstances();
    }

    /**
     * Test the readFile method of ContentServer.
     * This test ensures that the file can be read and parsed correctly.
     */
    @Test
    public void testReadFile() throws IOException {
        // Check if the test file exists
        assertTrue(Files.exists(Paths.get(testFilePath)), "Test file does not exist: " + testFilePath);

        // Read the file content
        List<String> jsonStrings = contentServer.readFilePublic(testFilePath);
        assertNotNull(jsonStrings, "readFilePublic returned null");
        assertFalse(jsonStrings.isEmpty(), "JSON strings list is empty");
        
        // Print file content and parsed JSON strings for debugging
        System.out.println("File content:");
        Files.lines(Paths.get(testFilePath)).forEach(System.out::println);
        
        System.out.println("Parsed JSON strings:");
        jsonStrings.forEach(System.out::println);
        
        // Parse JSON strings into JSONObjects
        ArrayList<JSONObject> jsonList = new ArrayList<>();
        for (String jsonString : jsonStrings) {
            jsonList.add(new JSONObject(jsonString));
        }
        
        // Ensure that at least one JSON object was parsed
        assertFalse(jsonList.isEmpty(), "JSON object list is empty");
    }

    /**
     * Test the sendJsons method of ContentServer.
     * This test ensures that JSON objects can be sent correctly.
     */
    @Test
    public void testSendJsons() throws IOException {
        ArrayList<JSONObject> jsonList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "test1");
        jsonObject.put("data", "sample data");
        jsonList.add(jsonObject);

        // Mock the socket
        Socket mockSocket = Mockito.mock(Socket.class);
        contentServer.setSocket(mockSocket);

        // Mock the output stream
        assertDoesNotThrow(() -> contentServer.sendJsons(jsonList));
    }

    /**
     * Test handling invalid JSON data.
     * This test ensures that an IOException is thrown when invalid JSON data is encountered.
     */
    @Test
    public void testInvalidJson() throws IOException {
        try (FileWriter writer = new FileWriter(invalidFilePath)) {
            writer.write("This is not a valid JSON");
        }

        // Create a new ContentServer with the invalid file path
        ContentServer invalidContentServer = new ContentServer(testAddress, testPort, invalidFilePath);
        invalidContentServer.setTestMode(true);
        
        // Ensure that an IOException is thrown when sending data
        Assertions.assertThrows(IOException.class, () -> {
            invalidContentServer.sendData();
        });
    }

    /**
     * Test adding server instances.
     * This test ensures that the server instances are added correctly up to the maximum limit.
     */
    @Test
    public void testAddServerInstance() {
        ContentServer server1 = new ContentServer(testAddress, testPort, testFilePath);
        ContentServer server2 = new ContentServer(testAddress, testPort + 1, testFilePath);
        ContentServer server3 = new ContentServer(testAddress, testPort + 2, testFilePath);
        ContentServer server4 = new ContentServer(testAddress, testPort + 3, testFilePath);

        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);
        ContentServer.addServerInstance(server3);
        ContentServer.addServerInstance(server4);

        assertEquals(3, ContentServer.getServerInstancesCount(), "Should only add up to MAX_SERVERS instances");
    }

    /**
     * Test removing server instances.
     * This test ensures that server instances are removed correctly.
     */
    @Test
    public void testRemoveServerInstance() {
        ContentServer server1 = new ContentServer(testAddress, testPort, testFilePath);
        ContentServer server2 = new ContentServer(testAddress, testPort + 1, testFilePath);

        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);

        assertEquals(2, ContentServer.getServerInstancesCount());

        ContentServer.removeServerInstance(server1);

        assertEquals(1, ContentServer.getServerInstancesCount());
    }

    /**
     * Test sending data to all servers.
     * This test ensures that data is sent to all server instances correctly.
     */
    @Test
    public void testSendDataToAllServers() throws IOException {
        ContentServer server1 = Mockito.spy(new ContentServer(testAddress, testPort, testFilePath));
        ContentServer server2 = Mockito.spy(new ContentServer(testAddress, testPort + 1, testFilePath));
        ContentServer server3 = Mockito.spy(new ContentServer(testAddress, testPort + 2, testFilePath));

        // Mock the sendJsons method for all servers
        Mockito.doNothing().when(server1).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doNothing().when(server2).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doNothing().when(server3).sendJsons(Mockito.<ArrayList<JSONObject>>any());

        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);
        ContentServer.addServerInstance(server3);

        List<JSONObject> testData = new ArrayList<>();
        testData.add(new JSONObject("{\"test\": \"data\"}"));

        ContentServer.sendDataToAllServers(testData);

        // Verify that the sendJsons method was called on all server instances
        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }

    /**
     * Test sending data to all servers with one server failing.
     * This test ensures that the data is still sent to the remaining servers.
     */
    @Test
    public void testSendDataToAllServersWithFailure() throws IOException {
        ContentServer server1 = Mockito.mock(ContentServer.class);
        ContentServer server2 = Mockito.mock(ContentServer.class);
        ContentServer server3 = Mockito.mock(ContentServer.class);

        // Mock the sendJsons method for server1 to throw an IOException
        Mockito.doThrow(new IOException("Simulated failure")).when(server1).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doThrow(new IOException("Simulated failure")).when(server2).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        // server3 will succeed
        Mockito.doNothing().when(server3).sendJsons(Mockito.<ArrayList<JSONObject>>any());

        ContentServer.clearServerInstances();
        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);
        ContentServer.addServerInstance(server3);

        List<JSONObject> testData = new ArrayList<>();
        testData.add(new JSONObject("{\"test\": \"data\"}"));

        assertDoesNotThrow(() -> ContentServer.sendDataToAllServers(testData));

        // Verify that the sendJsons method was called on all server instances
        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }

    /**
     * Test sending data to all servers with all servers failing.
     * This test ensures that an IOException is thrown when all server instances fail to send data.
     */
    @Test
    public void testSendDataToAllServersAllFail() throws IOException {
        ContentServer server1 = Mockito.spy(new ContentServer(testAddress, testPort, testFilePath));
        ContentServer server2 = Mockito.spy(new ContentServer(testAddress, testPort + 1, testFilePath));
        ContentServer server3 = Mockito.spy(new ContentServer(testAddress, testPort + 2, testFilePath));

        // Mock the sendJsons method for all servers to throw an IOException
        Mockito.doThrow(new IOException("Simulated failure")).when(server1).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doThrow(new IOException("Simulated failure")).when(server2).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doThrow(new IOException("Simulated failure")).when(server3).sendJsons(Mockito.<ArrayList<JSONObject>>any());

        ContentServer.clearServerInstances();
        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);
        ContentServer.addServerInstance(server3);

        List<JSONObject> testData = new ArrayList<>();
        testData.add(new JSONObject("{\"test\": \"data\"}"));

        assertThrows(IOException.class, () -> ContentServer.sendDataToAllServers(testData));

        // Verify that the sendJsons method was called on all server instances
        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }
}
