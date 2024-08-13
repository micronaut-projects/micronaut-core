package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class UnsupportedMediaExceptionTest {
    @Test
    void statusIsUnsupportedMediaType() {
        UnsupportedMediaException ex = new UnsupportedMediaException(MediaType.TEXT_HTML_TYPE, Collections.emptyList());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
    }
}