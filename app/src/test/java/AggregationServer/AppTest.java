package AggregationServer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test void appHasAGreeting() {
        AggregationServer classUnderTest = new AggregationServer();
        assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
    }
}
