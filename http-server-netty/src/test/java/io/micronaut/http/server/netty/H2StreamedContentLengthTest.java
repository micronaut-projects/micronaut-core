package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * An application Content-Length on an unknown-length streamed body must not be sent on HTTP/2.
 */
@MicronautTest
@Property(name = "spec.name", value = "H2StreamedContentLengthTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
class H2StreamedContentLengthTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void streamedBodyWithWrongApplicationContentLength() {
        HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET("/h2-streamed-length/flux"), byte[].class);
        Assertions.assertEquals(200, response.code());
        Assertions.assertEquals("0123456789", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Controller("/h2-streamed-length")
    @Requires(property = "spec.name", value = "H2StreamedContentLengthTest")
    static class Route {
        @Get("/flux")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<Flux<byte[]>> flux() {
            return HttpResponse.ok(Flux.just("01234".getBytes(StandardCharsets.UTF_8), "56789".getBytes(StandardCharsets.UTF_8)))
                .header("Content-Length", "3");
        }
    }
}
