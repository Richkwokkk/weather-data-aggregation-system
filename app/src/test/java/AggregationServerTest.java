import org.json.JSONObject;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Test class for AggregationServer functionality.
 * This class contains various test cases to verify the behavior of the AggregationServer.
 */
public class AggregationServerTest {
    private AggregationServer server;

    /**
     * Set up method run before each test.
     * Initializes a new AggregationServer instance and clears its data storage.
     */
    @BeforeEach
    public void setUp() {
        server = new AggregationServer(4567);
        server.clearDataStorage();
    }

    /**
     * Tear down method run after each test.
     * Stores an empty JSON object in the server to reset its state.
     */
    @AfterEach
    public void tearDown() {
        JSONObject emptyData = new JSONObject();
        emptyData.put("id", "");
        server.store(emptyData);
    }

    /**
     * Tear down method to clean up test files after each test.
     * Deletes both active and backup files if they exist.
     */
    @AfterEach
    public void tearDownTestFiles() throws IOException {
        Files.deleteIfExists(AggregationServer.getActiveFile());
        Files.deleteIfExists(AggregationServer.getBackupFile());
    }
    
    /**
     * Test recovering data with valid dummy data.
     */
    @Test
    public void testRecoverDataWithValidDummyData() {
        // Create and store dummy data
        String dummyData = "{\"id\":\"test1\", \"value\":\"data1\"}";
        server.store(new JSONObject(dummyData));

        // Assert that the data was stored correctly
        Assertions.assertTrue(server.getDataStorage().has("test1"));
        Assertions.assertEquals("data1", server.getDataStorage().getJSONObject("test1").getString("value"));
    }

    /**
     * Test storing dummy data.
     */
    @Test
    public void testStoreWithDummyData() {
        JSONObject jsonData = new JSONObject();
        jsonData.put("id", "test2");
        jsonData.put("value", "data2");

        // Store the data
        server.store(jsonData);

        // Assert that the data was stored correctly
        Assertions.assertTrue(server.getDataStorage().has("test2"));
        Assertions.assertEquals("data2", server.getDataStorage().getJSONObject("test2").getString("value"));
    }

    /**
     * Test storing invalid dummy data.
     */
    @Test
    public void testStoreWithInvalidDummyData() {
        JSONObject jsonData = new JSONObject();
        jsonData.put("value", "data3");

        // Store the data
        int initialSize = server.getDataStorage().length();
        server.store(jsonData);

        // Assert that the data was not stored correctly
        Assertions.assertEquals(initialSize, server.getDataStorage().length(), "Data storage size should not change");
        Assertions.assertFalse(server.getDataStorage().has(""));
    }

    /**
     * Test recovering data with invalid dummy JSON.
     */
    @Test
    public void testRecoverDataWithInvalidDummyJson() {
        String invalidData = "invalid json data";
        Assertions.assertThrows(Exception.class, () -> {
            server.store(new JSONObject(invalidData));
        });
    }

    /**
     * Test recovering data from the active file.
     */
    @Test
    public void testRecoverDataFromActiveFile() throws IOException {
        // Prepare test data and write it to the active file
        String testData = "{\"test3\":{\"id\":\"test3\", \"value\":\"data3\"}}";
        Files.write(AggregationServer.getActiveFile(), testData.getBytes());
        
        System.out.println("Active file content before recovery: " + new String(Files.readAllBytes(AggregationServer.getActiveFile())));
        
        // Recover data from the file
        server.recoverData();
        
        System.out.println("DataStorage after recovery: " + server.getDataStorage().toString());
        
        // Assert that the data was recovered correctly
        Assertions.assertTrue(server.getDataStorage().has("test3"), "DataStorage should contain 'test3'");
        Assertions.assertEquals("data3", server.getDataStorage().getJSONObject("test3").getString("value"), "Value for 'test3' should be 'data3'");
    }

    /**
     * Test recovering data from the backup file.
     */
    @Test
    public void testRecoverDataFromBackupFile() throws IOException {
        String testData = "{\"test4\":{\"id\":\"test4\", \"value\":\"data4\"}}";
        Files.write(AggregationServer.getBackupFile(), testData.getBytes());
        Files.deleteIfExists(AggregationServer.getActiveFile());
        
        System.out.println("Backup file content before recovery: " + new String(Files.readAllBytes(AggregationServer.getBackupFile())));
        
        // Recover data from the file
        server.recoverData();
        
        System.out.println("DataStorage after recovery: " + server.getDataStorage().toString());
        // Assert that the data was recovered correctly
        Assertions.assertTrue(server.getDataStorage().has("test4"), "DataStorage should contain 'test4'");
        if (server.getDataStorage().has("test4")) {
            Assertions.assertEquals("data4", server.getDataStorage().getJSONObject("test4").getString("value"), "Value for 'test4' should be 'data4'");
        } else {
            System.out.println("DataStorage does not contain 'test4'");
        }
    }

    /**
     * Test that the Janitor thread removes old data.
     */
    @Test
    public void testJanitorRemovesOldData() throws InterruptedException {
        // Prepare and store test data
        JSONObject testData = new JSONObject();
        testData.put("id", "oldData");
        testData.put("value", "shouldBeRemoved");
        
        server.store(testData);
        
        // Wait for the Janitor to run (assuming it runs every 30 seconds)
        Thread.sleep(31000);
        
        // Assert that the old data has been removed
        Assertions.assertFalse(server.getDataStorage().has("oldData"), "Old data should have been removed");
    }

    /**
     * Test concurrent data storage operations.
     */
    @Test
    public void testConcurrentDataStorage() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        // Create and start multiple threads to store data concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                JSONObject data = new JSONObject();
                data.put("id", "concurrent" + index);
                data.put("value", "data" + index);
                server.store(data);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Assert that all data was stored correctly
        for (int i = 0; i < threadCount; i++) {
            Assertions.assertTrue(server.getDataStorage().has("concurrent" + i));
        }
    }

}
