package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotFoundExceptionTest {
    @Test
    void statusIsNotFound() {
        NotFoundException ex = new NotFoundException();
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}