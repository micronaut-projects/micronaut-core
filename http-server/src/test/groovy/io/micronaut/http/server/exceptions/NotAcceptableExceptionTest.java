package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class NotAcceptableExceptionTest {

    @Test
    void statusIsNotAcceptable() {
        NotAcceptableException ex = new NotAcceptableException(Collections.emptyList(), Collections.emptyList());
        assertEquals(HttpStatus.NOT_ACCEPTABLE, ex.getStatus());
    }
}