package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotWebSocketRequestExceptionTest {
    @Test
    void statusIsBadRequest() {
        NotWebSocketRequestException ex = new NotWebSocketRequestException();
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}