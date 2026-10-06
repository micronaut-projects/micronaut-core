package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncDirectRoutesTest {

    @Test
    void aDirectRouteRunsOnAnExecutorOrCompletesLater() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "AsyncDirectRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            assertEquals("Q1: 1200 orders", http.retrieve(HttpRequest.GET("/reports/2026-q1")));
            assertEquals("42.00", http.retrieve(HttpRequest.GET("/quotes/MNT")));
            // declined: no ordinary route answers them
            for (String path : new String[] {"/reports/2025-q4", "/quotes/XYZ"}) {
                HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                    () -> http.retrieve(HttpRequest.GET(path)));
                assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
            }
        }
    }
}
