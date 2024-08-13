package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class NotAllowedExceptionTest {
    @Test
    void statusIsNotAllowed() {
        NotAllowedException ex = new NotAllowedException(Collections.emptySet(), "");
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, ex.getStatus());
    }
}