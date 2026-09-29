package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatedRoutesTest {

    @Test
    void theBeanMethodValidatesTheBody() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ValidatedRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            HttpResponse<?> created = client.toBlocking().exchange(HttpRequest.POST("/products", Map.of("name", "lamp")));
            assertEquals(HttpStatus.CREATED, created.getStatus());
            HttpClientResponseException invalid = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/products", Map.of("name", ""))));
            assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatus());
        }
    }
}
