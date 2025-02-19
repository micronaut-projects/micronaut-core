package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotAcceptableExceptionTest {

    @Test
    void statusIsNotAcceptable() {
        NotAcceptableException ex = new NotAcceptableException(List.of(MediaType.TEXT_HTML_TYPE.toString()), List.of(MediaType.APPLICATION_JSON_TYPE.toString()));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, ex.getStatus());
        assertEquals("Specified Accept Types [text/html] not supported. Supported types: [application/json]", ex.getMessage());
        assertEquals(List.of("text/html"), ex.getAcceptedTypes());
        assertEquals(List.of("application/json"), ex.getProduceableContentTypes());
    }
}