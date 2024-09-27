import org.json.JSONObject;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class AggregationServerTest {
    private AggregationServer server;

    @BeforeEach
    public void setUp() {
        server = new AggregationServer(4567);
        server.clearDataStorage();
    }

    @AfterEach
    public void tearDown() {
        JSONObject emptyData = new JSONObject();
        emptyData.put("id", "");
        server.store(emptyData);
    }

    @AfterEach
    public void tearDownTestFiles() throws IOException {
        Files.deleteIfExists(AggregationServer.getActiveFile());
        Files.deleteIfExists(AggregationServer.getBackupFile());
    }
    
    @Test
    public void testRecoverDataWithValidDummyData() {
        String dummyData = "{\"id\":\"test1\", \"value\":\"data1\"}";
        server.store(new JSONObject(dummyData));

        Assertions.assertTrue(server.getDataStorage().has("test1"));
        Assertions.assertEquals("data1", server.getDataStorage().getJSONObject("test1").getString("value"));
    }

    @Test
    public void testStoreWithDummyData() {
        JSONObject jsonData = new JSONObject();
        jsonData.put("id", "test2");
        jsonData.put("value", "data2");

        server.store(jsonData);
        Assertions.assertTrue(server.getDataStorage().has("test2"));
        Assertions.assertEquals("data2", server.getDataStorage().getJSONObject("test2").getString("value"));
    }

    @Test
    public void testStoreWithInvalidDummyData() {
        JSONObject jsonData = new JSONObject();
        jsonData.put("value", "data3");

        int initialSize = server.getDataStorage().length();
        server.store(jsonData);
        Assertions.assertEquals(initialSize, server.getDataStorage().length(), "Data storage size should not change");
        Assertions.assertFalse(server.getDataStorage().has(""));
    }

    @Test
    public void testRecoverDataWithInvalidDummyJson() {
        String invalidData = "invalid json data";
        Assertions.assertThrows(Exception.class, () -> {
            server.store(new JSONObject(invalidData));
        });
    }

    @Test
    public void testRecoverDataFromActiveFile() throws IOException {
        String testData = "{\"test3\":{\"id\":\"test3\", \"value\":\"data3\"}}";
        Files.write(AggregationServer.getActiveFile(), testData.getBytes());
        
        System.out.println("Active file content before recovery: " + new String(Files.readAllBytes(AggregationServer.getActiveFile())));
        
        server.recoverData();
        
        System.out.println("DataStorage after recovery: " + server.getDataStorage().toString());
        
        Assertions.assertTrue(server.getDataStorage().has("test3"), "DataStorage should contain 'test3'");
        Assertions.assertEquals("data3", server.getDataStorage().getJSONObject("test3").getString("value"), "Value for 'test3' should be 'data3'");
    }

    @Test
    public void testRecoverDataFromBackupFile() throws IOException {
        String testData = "{\"test4\":{\"id\":\"test4\", \"value\":\"data4\"}}";
        Files.write(AggregationServer.getBackupFile(), testData.getBytes());
        Files.deleteIfExists(AggregationServer.getActiveFile());
        
        System.out.println("Backup file content before recovery: " + new String(Files.readAllBytes(AggregationServer.getBackupFile())));
        
        server.recoverData();
        
        System.out.println("DataStorage after recovery: " + server.getDataStorage().toString());
        
        Assertions.assertTrue(server.getDataStorage().has("test4"), "DataStorage should contain 'test4'");
        if (server.getDataStorage().has("test4")) {
            Assertions.assertEquals("data4", server.getDataStorage().getJSONObject("test4").getString("value"), "Value for 'test4' should be 'data4'");
        } else {
            System.out.println("DataStorage does not contain 'test4'");
        }
    }

    @Test
    public void testJanitorRemovesOldData() throws InterruptedException {
        JSONObject testData = new JSONObject();
        testData.put("id", "oldData");
        testData.put("value", "shouldBeRemoved");
        
        server.store(testData);
        
        Thread.sleep(31000);
        
        Assertions.assertFalse(server.getDataStorage().has("oldData"), "Old data should have been removed");
    }

    @Test
    public void testConcurrentDataStorage() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
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
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        for (int i = 0; i < threadCount; i++) {
            Assertions.assertTrue(server.getDataStorage().has("concurrent" + i));
        }
    }

}
