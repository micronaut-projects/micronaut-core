package example.micronaut;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoggingTest {

    private static final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    private final PrintStream systemOut = System.out;

    private ByteArrayOutputStream byteArrayOutputStream;

    @BeforeEach
    public void setUp() {
        byteArrayOutputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(byteArrayOutputStream));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(systemOut);
    }

    @Test
    void testConsoleAppender() {
        Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.info("test message");
        assertEquals("test message\n", byteArrayOutputStream.toString());
    }
}
