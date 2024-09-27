import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

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
    }

    @AfterEach
    void cleanup() {
        new File(invalidFilePath).delete();
        new File(testFilePath).delete();
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
}
