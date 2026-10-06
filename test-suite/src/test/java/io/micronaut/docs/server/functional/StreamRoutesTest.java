package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamRoutesTest {

    @Test
    void theRoutesStreamTheirResponses() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "StreamRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            assertEquals("id: 3\ndata: 3\n\nid: 2\ndata: 2\n\nid: 1\ndata: 1\n\n", http.retrieve(HttpRequest.GET("/countdown/3")));
            assertEquals("data: tick 1\n\ndata: tick 2\n\ndata: tick 3\n\n", http.retrieve(HttpRequest.GET("/ticks")));
            assertEquals("data: a\n\ndata: b\n\n", http.retrieve(HttpRequest.POST("/words", "a b").contentType(MediaType.TEXT_PLAIN_TYPE)));
            assertEquals("[1,2,3]", http.retrieve(HttpRequest.GET("/numbers")));
            HttpResponse<String> notified = http.exchange(message("notify"), String.class);
            assertEquals(HttpStatus.ACCEPTED, notified.getStatus());
            assertEquals("s-1", notified.getHeaders().get("Session-Id"));
            assertEquals("{\"result\":\"pong\"}", http.retrieve(message("ping")));
            assertEquals("data: received hello\n\n", http.retrieve(message("hello")));
        }
    }

    private static HttpRequest<String> message(String message) {
        return HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE);
    }
}
