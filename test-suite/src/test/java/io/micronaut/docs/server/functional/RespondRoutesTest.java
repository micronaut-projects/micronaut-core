package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
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

class RespondRoutesTest {

    @Test
    void theRoutesAnswerWithoutAHandler() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "RespondRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            HttpResponse<String> ping = http.exchange(HttpRequest.GET("/ping"), String.class);
            assertEquals("pong", ping.body());
            assertEquals("max-age=60", ping.getHeaders().get("Cache-Control"));
            // the client follows the redirect
            assertEquals("pong", http.retrieve(HttpRequest.GET("/old-ping")));
            assertEquals("visit 1", http.retrieve(HttpRequest.GET("/visits")));
            assertEquals("visit 2", http.retrieve(HttpRequest.GET("/visits")));
            assertEquals("Hello World", http.retrieve(HttpRequest.GET("/greetings/World")));
            HttpClientResponseException gone = assertThrows(HttpClientResponseException.class,
                () -> http.retrieve(HttpRequest.POST("/legacy/webhook", "{}")));
            assertEquals(HttpStatus.GONE, gone.getStatus());
        }
    }
}
