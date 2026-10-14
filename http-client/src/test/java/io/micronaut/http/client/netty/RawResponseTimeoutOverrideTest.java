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

/**
 * The response timeout of a raw exchange can shorten the wait for the response, and the read
 * timeout of the connection still applies, over HTTP/1.1 and HTTP/2.
 */
class RawResponseTimeoutOverrideTest {
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void longerResponseTimeoutDoesNotOutlastTheReadTimeout(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, 2000, Duration.ofSeconds(10)).close());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void shorterResponseTimeoutFailsTheRequest(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, 5000, Duration.ofMillis(100)).close());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Assertions.assertTrue(elapsed.compareTo(READ_TIMEOUT) < 0, "The exchange failed after " + elapsed);

            // the timed out request did not leave the client without a usable connection
            try (ByteBodyHttpResponse<?> response = exchange(client, server, 0, Duration.ofSeconds(10))) {
                Assertions.assertEquals(200, response.code());
            }
        }
    }

    private static ApplicationContext start(int version) {
        boolean ssl = version == 2;
        return ApplicationContext.run(Map.of(
            "spec.name", "RawResponseTimeoutOverrideTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", ssl,
            "micronaut.http.client.alpn-modes", ssl ? "h2" : "http/1.1",
            "micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms",
            "micronaut.server.http-version", ssl ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", ssl,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, EmbeddedServer server, long delay, Duration responseTimeout) {
        HttpRequest<?> request = HttpRequest.GET(server.getURI().resolve("/raw-response-timeout-override/slow?delay=" + delay));
        RawRequestOptions options = RawRequestOptions.builder().responseTimeout(responseTimeout).build();
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, options)).block();
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/raw-response-timeout-override")
    @Requires(property = "spec.name", value = "RawResponseTimeoutOverrideTest")
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn("slow");
        }
    }
}
