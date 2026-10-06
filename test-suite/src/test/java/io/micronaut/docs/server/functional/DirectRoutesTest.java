package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectRoutesTest {

    @Test
    void theServerAnswersBeforeTheRequestIsCreated() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "DirectRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            assertEquals("UP", http.retrieve(HttpRequest.GET("/probe/live")));
            assertEquals("db UP", http.retrieve(HttpRequest.GET("/probe/db")));
            // a declined request continues to the ordinary route
            assertEquals("cached logo.png", http.retrieve(HttpRequest.GET("/assets/logo.png")));
            assertEquals("rendered readme.txt", http.retrieve(HttpRequest.GET("/assets/readme.txt")));
            assertEquals("User-agent: *\nDisallow: /private/\n", http.retrieve(HttpRequest.GET("/robots.txt")));
            // a conditional GET: the function reads the request
            HttpResponse<String> version = http.exchange(HttpRequest.GET("/version"), String.class);
            assertEquals("1.0.0", version.body());
            assertEquals("\"1.0.0\"", version.header(HttpHeaders.ETAG));
            HttpResponse<String> notModified = http.exchange(HttpRequest.GET("/version").header(HttpHeaders.IF_NONE_MATCH, "\"1.0.0\""), String.class);
            assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus());
            HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
                () -> http.retrieve(HttpRequest.POST("/orders", "{}").header("User-Agent", "BadBot/1.0")));
            assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
            // no direct route matches: the request continues to the ordinary routes, here none
            HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                () -> http.retrieve(HttpRequest.GET("/probe/queue")));
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
        }
    }
}
