package ContentServer;

import org.junit.jupiter.api.*;
import org.mockito.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContentServerTest {

    private ContentServer contentServer;
    private static final String TEST_SERVER_URL = "http://localhost:8080";
    private static final String TEST_FILE_PATH = "test_weather_data.txt";

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary file with test weather data
        String testData = "id: ABC123\n" +
                          "name: Test Station\n" +
                          "temperature: 25.5\n" +
                          "humidity: 60";
        Files.write(Paths.get(TEST_FILE_PATH), testData.getBytes());

        contentServer = new ContentServer(TEST_SERVER_URL, TEST_FILE_PATH);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Delete the temporary file
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
    }

    @Test
    void testLoadWeatherData() throws Exception {
        // Use reflection to access private method
        java.lang.reflect.Method loadWeatherDataMethod = ContentServer.class.getDeclaredMethod("loadWeatherData");
        loadWeatherDataMethod.setAccessible(true);
        loadWeatherDataMethod.invoke(contentServer);

        // Use reflection to access private field
        java.lang.reflect.Field weatherDataField = ContentServer.class.getDeclaredField("weatherData");
        weatherDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> weatherData = (java.util.Map<String, String>) weatherDataField.get(contentServer);

        assertNotNull(weatherData);
        assertEquals("ABC123", weatherData.get("id"));
        assertEquals("Test Station", weatherData.get("name"));
        assertEquals("25.5", weatherData.get("temperature"));
        assertEquals("60", weatherData.get("humidity"));
    }

    @Test
    void testSendUpdate() throws Exception {
        // Mock HttpURLConnection
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        URL mockUrl = mock(URL.class);
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return mockConnection;
            }
        };
        URL url = URL.of(new URI("http://localhost:8080"), handler);

        // Set up mock behavior
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("1");

        // Use reflection to access private method
        java.lang.reflect.Method sendUpdateMethod = ContentServer.class.getDeclaredMethod("sendUpdate");
        sendUpdateMethod.setAccessible(true);

        // Execute the method
        sendUpdateMethod.invoke(contentServer);

        // Verify that the connection was properly configured
        verify(mockConnection).setRequestMethod("PUT");
        verify(mockConnection).setDoOutput(true);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty(eq("Lamport-Clock"), anyString());
    }
}
