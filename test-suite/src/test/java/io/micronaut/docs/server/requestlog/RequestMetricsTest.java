package io.micronaut.docs.server.requestlog;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMetricsTest {

    @Test
    void theListenerSeesTheRouteTemplate() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "RequestMetricsTest",
            "micronaut.server.netty.request-log-enabled", true));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            assertEquals("book 1", client.toBlocking().retrieve(HttpRequest.GET("/books/1")));
            RequestMetrics metrics = server.getApplicationContext().getBean(RequestMetrics.class);
            long deadline = System.currentTimeMillis() + 10_000;
            while (metrics.nanosByRoute().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(metrics.nanosByRoute().containsKey("/books/{id} 200"), metrics.nanosByRoute()::toString);
        }
    }

    @Requires(property = "spec.name", value = "RequestMetricsTest")
    @Controller("/books")
    static class BookController {
        @Get("/{id}")
        String book(String id) {
            return "book " + id;
        }
    }
}
