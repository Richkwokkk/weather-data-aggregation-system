package GETClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GETClientTest {

    private GETClient getClient;

    @Mock
    private HttpURLConnection mockConnection;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        getClient = new GETClient();

        // Set up the mock connection
        URI uri = new URI("http://localhost:8080");
        when(mockConnection.getURL()).thenReturn(uri.toURL());
    }

    @Test
    void testGetWeatherDataSuccess() throws Exception {
        // Prepare mock response
        String mockResponse = "[{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"state\":\"SA\",\"time_zone\":\"CST\",\"lat\":-34.9,\"lon\":138.6,\"local_date_time\":\"15/04:00pm\",\"local_date_time_full\":\"20230715160000\",\"air_temp\":13.3,\"apparent_t\":9.5,\"cloud\":\"Partly cloudy\",\"dewpt\":5.7,\"press\":1023.9,\"rel_hum\":60,\"wind_dir\":\"S\",\"wind_spd_kmh\":15,\"wind_spd_kt\":8}]";
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(mockResponse.getBytes()));

        // Redirect System.out to capture output
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Call the method
        getClient.getWeatherData("http://localhost:8080", null);

        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Current Weather Data:"));
        assertTrue(output.contains("id: IDS60901"));
        assertTrue(output.contains("name: Adelaide"));

        // Reset System.out
        System.setOut(System.out);
    }

    @Test
    void testGetWeatherDataFailure() throws Exception {
        // Simulate a server error
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);

        // Redirect System.out to capture output
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Call the method
        getClient.getWeatherData("http://localhost:8080", null);

        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("GET request failed. Response Code: 500"));

        // Reset System.out
        System.setOut(System.out);
    }

    // Add more tests as needed
}
