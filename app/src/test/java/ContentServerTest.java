import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContentServerTest {
    private ContentServer contentServer;
    private String testAddress = "localhost";
    private int testPort = 8080;
    private String testFilePath = "test_weather.json";
    private String invalidFilePath = "invalid_test_weather.json";

    @BeforeEach
    public void setUp() throws IOException {
        contentServer = new ContentServer(testAddress, testPort, testFilePath);
        contentServer.setTestMode(true);

        Files.deleteIfExists(Paths.get(testFilePath));

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

        // Clear existing server instances
        ContentServer.clearServerInstances();
    }

    @AfterEach
    void cleanup() {
        new File(invalidFilePath).delete();
        new File(testFilePath).delete();

        // Clear server instances after each test
        ContentServer.clearServerInstances();
    }

    @Test
    public void testReadFile() throws IOException {
        assertTrue(Files.exists(Paths.get(testFilePath)), "Test file does not exist: " + testFilePath);

        List<String> jsonStrings = contentServer.readFilePublic(testFilePath);
        assertNotNull(jsonStrings, "readFilePublic returned null");
        assertFalse(jsonStrings.isEmpty(), "JSON strings list is empty");
        
        System.out.println("File content:");
        Files.lines(Paths.get(testFilePath)).forEach(System.out::println);
        
        System.out.println("Parsed JSON strings:");
        jsonStrings.forEach(System.out::println);
        
        ArrayList<JSONObject> jsonList = new ArrayList<>();
        for (String jsonString : jsonStrings) {
            jsonList.add(new JSONObject(jsonString));
        }
        
        assertFalse(jsonList.isEmpty(), "JSON object list is empty");
    }

    @Test
    public void testSendJsons() throws IOException {
        ArrayList<JSONObject> jsonList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "test1");
        jsonObject.put("data", "sample data");
        jsonList.add(jsonObject);

        Socket mockSocket = Mockito.mock(Socket.class);
        contentServer.setSocket(mockSocket);

        assertDoesNotThrow(() -> contentServer.sendJsons(jsonList));
    }

    @Test
    public void testInvalidJson() throws IOException {
        try (FileWriter writer = new FileWriter(invalidFilePath)) {
            writer.write("This is not a valid JSON");
        }

        ContentServer invalidContentServer = new ContentServer(testAddress, testPort, invalidFilePath);
        invalidContentServer.setTestMode(true);
        
        Assertions.assertThrows(IOException.class, () -> {
            invalidContentServer.sendData();
        });
    }

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

    @Test
    public void testSendDataToAllServers() throws IOException {
        ContentServer server1 = Mockito.spy(new ContentServer(testAddress, testPort, testFilePath));
        ContentServer server2 = Mockito.spy(new ContentServer(testAddress, testPort + 1, testFilePath));
        ContentServer server3 = Mockito.spy(new ContentServer(testAddress, testPort + 2, testFilePath));

        Mockito.doNothing().when(server1).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doNothing().when(server2).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.doNothing().when(server3).sendJsons(Mockito.<ArrayList<JSONObject>>any());

        ContentServer.addServerInstance(server1);
        ContentServer.addServerInstance(server2);
        ContentServer.addServerInstance(server3);

        List<JSONObject> testData = new ArrayList<>();
        testData.add(new JSONObject("{\"test\": \"data\"}"));

        ContentServer.sendDataToAllServers(testData);

        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }

    @Test
    public void testSendDataToAllServersWithFailure() throws IOException {
        ContentServer server1 = Mockito.mock(ContentServer.class);
        ContentServer server2 = Mockito.mock(ContentServer.class);
        ContentServer server3 = Mockito.mock(ContentServer.class);

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

        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }

    @Test
    public void testSendDataToAllServersAllFail() throws IOException {
        ContentServer server1 = Mockito.spy(new ContentServer(testAddress, testPort, testFilePath));
        ContentServer server2 = Mockito.spy(new ContentServer(testAddress, testPort + 1, testFilePath));
        ContentServer server3 = Mockito.spy(new ContentServer(testAddress, testPort + 2, testFilePath));

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

        Mockito.verify(server1, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server2, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
        Mockito.verify(server3, Mockito.times(1)).sendJsons(Mockito.<ArrayList<JSONObject>>any());
    }
}
