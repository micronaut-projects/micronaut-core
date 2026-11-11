package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The read idle timeout of a raw exchange replaces the read timeout of the client for that
 * exchange: longer or shorter, over HTTP/1.1 and HTTP/2.
 */
class RawReadIdleTimeoutTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aLongerReadIdleTimeoutOutlastsTheReadTimeoutOfTheClient(int version) throws Exception {
        try (ApplicationContext ctx = start(version, "500ms");
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            // without it, the read timeout of the client applies
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, 1500, null).close());
            try (ByteBodyHttpResponse<?> response = exchange(client, server, 1500, Duration.ofSeconds(5))) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("slow", response.byteBody().buffer().get(5, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
            // the next exchange has the read timeout of the client again
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, 1500, null).close());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aShorterReadIdleTimeoutFailsTheExchange(int version) throws Exception {
        try (ApplicationContext ctx = start(version, "10s");
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, 5000, Duration.ofMillis(200)).close());
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(3)) < 0);
            try (ByteBodyHttpResponse<?> response = exchange(client, server, 0, null)) {
                Assertions.assertEquals(200, response.code());
            }
        }
    }

    private static ApplicationContext start(int version, String readTimeout) {
        boolean ssl = version == 2;
        return ApplicationContext.run(Map.of(
            "spec.name", "RawReadIdleTimeoutTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", ssl,
            "micronaut.http.client.alpn-modes", ssl ? "h2" : "http/1.1",
            "micronaut.http.client.read-timeout", readTimeout,
            "micronaut.server.http-version", ssl ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", ssl,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1,
            "micronaut.server.idle-timeout", "10s"
        ));
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, EmbeddedServer server, long delay, Duration readIdleTimeout) {
        HttpRequest<?> request = HttpRequest.GET(server.getURI().resolve("/raw-read-idle/slow?delay=" + delay));
        RawRequestOptions options = RawRequestOptions.builder().readIdleTimeout(readIdleTimeout).build();
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, options)).block();
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/raw-read-idle")
    @Requires(property = "spec.name", value = "RawReadIdleTimeoutTest")
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn("slow");
        }
    }
}
