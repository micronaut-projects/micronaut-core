package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublisherStringBodyTest {
    @Test
    public void multiByteCharactersSpanningBuffers() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PublisherStringBodyTest",
            "micronaut.server.port", "0"
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();

            // large enough to arrive in many buffers, with 2 and 3 byte characters spanning their boundaries
            String text = "é日".repeat(100_000);
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(server.getURL() + "/publisher-string"))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(text))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(text.length() + ":" + text.hashCode(), response.body());
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "PublisherStringBodyTest")
    public static class MyController {
        @Post(value = "/publisher-string", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        public Mono<String> echo(@Body Publisher<String> body) {
            return Flux.from(body)
                .collect(StringBuilder::new, StringBuilder::append)
                .map(sb -> sb.length() + ":" + sb.toString().hashCode());
        }
    }
}
