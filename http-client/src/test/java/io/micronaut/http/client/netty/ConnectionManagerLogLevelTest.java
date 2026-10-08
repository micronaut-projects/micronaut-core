package io.micronaut.http.client.netty;

import io.micronaut.logging.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionManagerLogLevelTest {

    @ParameterizedTest
    @EnumSource(value = LogLevel.class, names = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"})
    void mapsToTheNettyLevelOfTheSameName(LogLevel logLevel) {
        assertEquals(io.netty.handler.logging.LogLevel.valueOf(logLevel.name()), ConnectionManager.toNettyLogLevel(logLevel));
    }

    @Test
    void rejectsALevelNettyDoesNotHave() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionManager.toNettyLogLevel(LogLevel.OFF));
    }
}
