package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BenchmarkRoutesTest {

    @Test
    void thePlaintextAndJsonTests() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "BenchmarkRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            HttpResponse<String> plaintext = http.exchange(HttpRequest.GET("/plaintext"), String.class);
            assertEquals("Hello, World!", plaintext.body());
            assertEquals("text/plain", plaintext.getHeaders().get("Content-Type"));
            assertEquals("13", plaintext.getHeaders().get("Content-Length"));
            assertEquals("Micronaut", plaintext.getHeaders().get("Server"));
            ZonedDateTime.parse(plaintext.getHeaders().get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME);
            assertNull(plaintext.getHeaders().get("Content-Encoding"));

            HttpResponse<String> json = http.exchange(HttpRequest.GET("/json"), String.class);
            assertEquals("{\"message\":\"Hello, World!\"}", json.body());
            assertEquals("application/json", json.getHeaders().get("Content-Type"));
            assertEquals("27", json.getHeaders().get("Content-Length"));
            assertEquals("Micronaut", json.getHeaders().get("Server"));
            ZonedDateTime.parse(json.getHeaders().get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME);
        }
    }

    @Test
    void theDateOfTheRoutesChangesAcrossSeconds() throws InterruptedException {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "BenchmarkRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            String first = http.exchange(HttpRequest.GET("/plaintext"), String.class).getHeaders().get("Date");
            String next = first;
            long deadline = System.currentTimeMillis() + 5000;
            while (next.equals(first) && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
                next = http.exchange(HttpRequest.GET("/plaintext"), String.class).getHeaders().get("Date");
            }
            assertNotEquals(first, next);
        }
    }
}
