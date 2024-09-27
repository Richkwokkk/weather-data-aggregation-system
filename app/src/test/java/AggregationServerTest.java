import org.json.JSONObject;
import org.junit.jupiter.api.*;

public class AggregationServerTest {
    private AggregationServer server;

    @BeforeEach
    public void setUp() {
        server = new AggregationServer(4567);
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

        server.store(jsonData);
        Assertions.assertFalse(server.getDataStorage().has(""));
    }

    @Test
    public void testRecoverDataWithInvalidDummyJson() {
        String invalidData = "invalid json data";
        Assertions.assertThrows(Exception.class, () -> {
            server.store(new JSONObject(invalidData));
        });
    }

    @AfterEach
    public void tearDown() {
        JSONObject emptyData = new JSONObject();
        emptyData.put("id", "");
        server.store(emptyData);
    }
}
