package io.micronaut.http.server.exceptions;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.CookieSizeExceededException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "CookieSizeExceededHandlerTest")
@MicronautTest
class CookieSizeExceededHandlerTest {
    @Test
    void testCookieSizeExceeded(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpRequest<?> request = HttpRequest.GET("/cookie-size-exceeded");
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
            () -> client.exchange(request));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        Optional<String> jsonOptional = ex.getResponse().getBody(String.class);
        assertTrue(jsonOptional.isPresent());
        String json = jsonOptional.get();
        assertNotNull(json);
        assertTrue(json.contains("The cookie [foo] byte size [4100] exceeds the maximum cookie size [4096]"));
    }

    @Requires(property = "spec.name", value = "CookieSizeExceededHandlerTest")
    @Controller("/cookie-size-exceeded")
    static class CookieSizeExceededExceptionController {
        @Get
        @Status(HttpStatus.OK)
        void index() {
            throw new CookieSizeExceededException("foo", 4096, 4100);
        }
    }
}
