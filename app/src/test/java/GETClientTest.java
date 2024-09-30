import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import java.io.*;

public class GETClientTest {

    @Test
    public void testSendRequest() throws Exception {
        String address = "localhost";
        int port = 4567;
        String request = "GET /weather.json HTTP/1.1\r\nLamport-Clock: 0\r\n\r\n";

        GETClient.NetworkClient mockNetworkClient = mock(GETClient.NetworkClient.class);

        JSONObject expectedResponse = new JSONObject();
        expectedResponse.put("key", "expectedValue");
        when(mockNetworkClient.sendRequest(address, port, request)).thenReturn(expectedResponse);

        GETClient client = new GETClient(mockNetworkClient);

        JSONObject response = client.request(address, port, request);

        assertNotNull(response);
        assertEquals("expectedValue", response.getString("key"));

        verify(mockNetworkClient).sendRequest(address, port, request);
    }

    @Test
    public void testProcessResponse() {
        BufferedReader input = new BufferedReader(new StringReader("Lamport-Clock: 1\n{\"key\":\"value\"}\n"));

        try {
            JSONObject result = GETClient.processResponse(input);
            assertNotNull(result);
            assertEquals("value", result.getString("key"));
        } catch (IOException e) {
            fail("IOException should not have been thrown: " + e.getMessage());
        }
    }

    @Test
    public void testPrintJson() {
        JSONObject json = new JSONObject();
        json.put("station1", new JSONObject().put("id", "station1").put("data", "value1"));
        json.put("station2", new JSONObject().put("id", "station2").put("data", "value2"));

        String stationID = "station1";
        
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        GETClient.printJson(json, stationID);

        System.setOut(originalOut);

        String capturedOutput = outContent.toString().trim();

        assertTrue(capturedOutput.contains("station1"));
        assertTrue(capturedOutput.contains("value1"));
        assertFalse(capturedOutput.contains("station2"));
        assertFalse(capturedOutput.contains("value2"));
    }
}
