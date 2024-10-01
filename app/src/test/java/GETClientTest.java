import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import java.io.*;

/**
 * Test class for GETClient functionality.
 */
public class GETClientTest {

    /**
     * Tests the sendRequest method of GETClient.
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testSendRequest() throws Exception {
        // Set up test parameters
        String address = "localhost";
        int port = 4567;
        String request = "GET /weather.json HTTP/1.1\r\nLamport-Clock: 0\r\n\r\n";

        // Create a mock NetworkClient
        GETClient.NetworkClient mockNetworkClient = mock(GETClient.NetworkClient.class);

        // Set up expected response
        JSONObject expectedResponse = new JSONObject();
        expectedResponse.put("key", "expectedValue");
        when(mockNetworkClient.sendRequest(address, port, request)).thenReturn(expectedResponse);

        // Create GETClient instance with mock NetworkClient
        GETClient client = new GETClient(mockNetworkClient);

        // Send request and get response
        JSONObject response = client.request(address, port, request);

        // Assert response is not null and contains expected value
        assertNotNull(response);
        assertEquals("expectedValue", response.getString("key"));

        // Verify that sendRequest was called with correct parameters
        verify(mockNetworkClient).sendRequest(address, port, request);
    }

    /**
     * Tests the processResponse method of GETClient.
     */
    @Test
    public void testProcessResponse() {
        // Create a BufferedReader with test input
        BufferedReader input = new BufferedReader(new StringReader("Lamport-Clock: 1\n{\"key\":\"value\"}\n"));

        try {
            // Process the response
            JSONObject result = GETClient.processResponse(input);
            
            // Assert result is not null and contains expected value
            assertNotNull(result);
            assertEquals("value", result.getString("key"));
        } catch (IOException e) {
            fail("IOException should not have been thrown: " + e.getMessage());
        }
    }

    /**
     * Tests the printJson method of GETClient.
     */
    @Test
    public void testPrintJson() {
        // Create test JSON object
        JSONObject json = new JSONObject();
        json.put("station1", new JSONObject().put("id", "station1").put("data", "value1"));
        json.put("station2", new JSONObject().put("id", "station2").put("data", "value2"));

        String stationID = "station1";
        
        // Redirect System.out to capture printed output
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        // Call printJson method
        GETClient.printJson(json, stationID);

        // Restore original System.out
        System.setOut(originalOut);

        // Get captured output as string
        String capturedOutput = outContent.toString().trim();

        // Assert that output contains expected content
        assertTrue(capturedOutput.contains("station1"));
        assertTrue(capturedOutput.contains("value1"));
        assertFalse(capturedOutput.contains("station2"));
        assertFalse(capturedOutput.contains("value2"));
    }
}
