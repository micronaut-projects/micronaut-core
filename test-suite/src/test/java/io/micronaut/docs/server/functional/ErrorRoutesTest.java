package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ErrorRoutesTest"));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void aGlobalErrorRoute() {
        HttpClientResponseException error = fails(() -> client.toBlocking().retrieve(HttpRequest.GET("/orders/5")));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals(Map.of("error", "No order 5"), body(error));
    }

    @Test
    void aGlobalStatusRoute() {
        HttpClientResponseException error = fails(() -> client.toBlocking().retrieve(HttpRequest.GET("/nothing/here")));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals(Map.of("error", "Nothing at /nothing/here"), body(error));
    }

    @Test
    void theErrorRoutesOfAGroupAreLocalToItsRoutes() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("ordered 2", http.retrieve(HttpRequest.POST("/checkout/2", "")));

        HttpClientResponseException invalid = fails(() -> http.retrieve(HttpRequest.POST("/checkout/0", "")));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatus());
        assertEquals(Map.of("invalid", "quantity must be positive"), body(invalid));

        HttpClientResponseException conflict = fails(() -> http.retrieve(HttpRequest.POST("/checkout/11", "")));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
        assertEquals(Map.of("conflict", "not enough stock"), body(conflict));

        // the error route of the group does not answer for a route outside of it
        HttpClientResponseException outside = fails(() -> http.retrieve(HttpRequest.GET("/pricing/0")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, outside.getStatus());
    }

    private static HttpClientResponseException fails(Executable executable) {
        return assertThrows(HttpClientResponseException.class, executable);
    }

    private static Map<String, String> body(HttpClientResponseException error) {
        return error.getResponse().getBody(Argument.mapOf(String.class, String.class)).orElseThrow();
    }
}
