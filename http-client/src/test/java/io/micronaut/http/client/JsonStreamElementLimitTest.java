package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * an element of a reactive jsonStream is buffered whole and must
 * be limited by max-content-length.
 */
class JsonStreamElementLimitTest {
    private static final String SPEC = "JsonStreamElementLimitTest";
    private static final int LIMIT = 1024;
    private static final Argument<Map<String, String>> ELEMENT = Argument.mapOf(String.class, String.class);

    private static EmbeddedServer server;
    private static StreamingHttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, CollectionUtils.mapOf("spec.name", SPEC, "micronaut.http.client.max-content-length", LIMIT));
        client = server.getApplicationContext().createBean(StreamingHttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
    }

    @Test
    void smallElementsOfALargeBodyAreStreamed() {
        assertEquals(8, jsonStream("/element-limit/stream?size=" + LIMIT / 2 + "&count=8").size());
    }

    @Test
    void anElementLargerThanTheLimitIsRejected() {
        assertThrows(Exception.class, () -> jsonStream("/element-limit/stream?size=" + 4 * LIMIT + "&count=1"));
    }

    private static List<Map<String, String>> jsonStream(String uri) {
        return Flux.from(client.jsonStream(HttpRequest.GET(uri), ELEMENT)).collectList().block(Duration.ofSeconds(10));
    }

    @Controller("/element-limit")
    @Requires(property = "spec.name", value = SPEC)
    static class ElementLimitController {
        @Get(uri = "/stream", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<byte[]> stream(int count, int size) {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < count; i++) {
                json.append("{\"text\":\"").append("x".repeat(size)).append("\"}\n");
            }
            List<byte[]> pieces = new ArrayList<>();
            int piece = LIMIT / 4;
            for (int i = 0; i < json.length(); i += piece) {
                pieces.add(json.substring(i, Math.min(json.length(), i + piece)).getBytes(StandardCharsets.UTF_8));
            }
            return Flux.fromIterable(pieces).delayElements(Duration.ofMillis(5));
        }
    }
}
