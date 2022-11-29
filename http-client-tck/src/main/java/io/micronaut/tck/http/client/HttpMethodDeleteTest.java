package io.micronaut.tck.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public interface HttpMethodDeleteTest {
    @Test
    default void deleteMethodMapping() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "HttpMethodDeleteTest"))) {
            try (HttpClient httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
                BlockingHttpClient client = httpClient.toBlocking();
                assertDoesNotThrow(() -> client.exchange(HttpRequest.DELETE("/delete")));
                assertEquals(HttpStatus.NO_CONTENT, client.exchange(HttpRequest.DELETE("/delete")).getStatus());
            }
        }
    }
}
