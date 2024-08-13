package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotAllowedExceptionTest {
    @Test
    void statusIsNotAllowed() {
        NotAllowedException ex = new NotAllowedException("PUT", URI.create("/foo"), Set.of("POST"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, ex.getStatus());
        assertEquals("PUT", ex.getRequestMethod());
        assertEquals(URI.create("/foo"), ex.getUri());
        assertEquals(Set.of("POST"), ex.getAllowedMethods());
        assertEquals("Method [PUT] not allowed for URI [/foo]. Allowed methods: [POST]", ex.getMessage());
    }
}