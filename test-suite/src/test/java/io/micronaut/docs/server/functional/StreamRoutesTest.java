package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            // an error before the first event is answered by the error routes
            assertEquals("data: order 1 shipped\n\n", http.retrieve(HttpRequest.GET("/orders/1/updates")));
            HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class, () -> http.retrieve(HttpRequest.GET("/orders/2/updates")));
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
            // after it, as an event
            assertEquals("data: started\n\ndata: done\n\n", http.retrieve(HttpRequest.GET("/jobs/1")));
            assertEquals("data: started\n\nevent: error\ndata: the job failed\n\n", http.retrieve(HttpRequest.GET("/jobs/2")));
            // a reconnecting client resumes after the last event it received
            assertEquals("id: 1\nretry: 5000\ndata: item 1\n\nid: 2\ndata: item 2\n\nid: 3\ndata: item 3\n\n", http.retrieve(HttpRequest.GET("/feed")));
            assertEquals("id: 2\nretry: 5000\ndata: item 2\n\nid: 3\ndata: item 3\n\n", http.retrieve(HttpRequest.GET("/feed").header("Last-Event-ID", "1")));
            HttpResponse<String> notified = http.exchange(message("notify"), String.class);
            assertEquals(HttpStatus.ACCEPTED, notified.getStatus());
            assertEquals("s-1", notified.getHeaders().get("Session-Id"));
            assertEquals("{\"result\":\"pong\"}", http.retrieve(message("ping")));
            assertEquals("data: received hello\n\n", http.retrieve(message("hello")));
            // a client that accepts only JSON
            assertEquals("{\"result\":\"pong\"}", http.retrieve(HttpRequest.POST("/messages", "ping")
                .contentType(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.APPLICATION_JSON_TYPE)));
        }
    }

    private static HttpRequest<String> message(String message) {
        return HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE);
    }
}
