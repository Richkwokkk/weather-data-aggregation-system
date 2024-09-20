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
        String testData = "id: ABC123\n" +
                          "name: Test Station\n" +
                          "temperature: 25.5\n" +
                          "humidity: 60";
        Files.write(Paths.get(TEST_FILE_PATH), testData.getBytes());

        contentServer = new ContentServer(TEST_SERVER_URL, TEST_FILE_PATH) {
            @Override
            protected HttpURLConnection createConnection(String url) throws IOException {
                return mock(HttpURLConnection.class);
            }
        };
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
    }

    @Test
    void testLoadWeatherData() throws Exception {
        java.lang.reflect.Method loadWeatherDataMethod = ContentServer.class.getDeclaredMethod("loadWeatherData");
        loadWeatherDataMethod.setAccessible(true);
        loadWeatherDataMethod.invoke(contentServer);

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
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(mockConnection.getHeaderField("Lamport-Clock")).thenReturn("1");

        ContentServer spyContentServer = spy(contentServer);
        spyContentServer.setMaxRetries(1);

        doReturn(mockConnection).when(spyContentServer).createConnection(anyString());

        spyContentServer.testSendUpdate();

        verify(mockConnection, times(1)).setRequestMethod("PUT");
        verify(mockConnection, times(1)).setDoOutput(true);
        verify(mockConnection, times(1)).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection, times(1)).setRequestProperty(eq("Lamport-Clock"), anyString());

        verify(spyContentServer, times(1)).createConnection(anyString());
    }
}
