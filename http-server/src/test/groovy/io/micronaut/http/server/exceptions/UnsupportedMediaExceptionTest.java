package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnsupportedMediaExceptionTest {
    @Test
    void statusIsUnsupportedMediaType() {
        UnsupportedMediaException ex = new UnsupportedMediaException(MediaType.TEXT_HTML, List.of(MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
        assertEquals("text/html", ex.getContentType());
        assertEquals(List.of("application/json"), ex.getAcceptableContentTypes());
        assertEquals("Content Type [text/html] not allowed. Allowed types: [application/json]", ex.getMessage());
    }
}