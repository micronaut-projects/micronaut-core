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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The read timeout of an HTTP/2 connection applies to each stream: a request that uses it times
 * out alone, and a concurrent request with a longer response timeout of its own on the same
 * connection still gets its response.
 */
class Http2StreamReadTimeoutTest {
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void defaultTimeoutStreamTimesOutAloneNextToAnOverride(boolean tls) throws Exception {
        try (ApplicationContext ctx = start(tls);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            // opens the connection
            String port = body(exchange(client, server, 0, null));

            long start = System.nanoTime();
            CompletableFuture<HttpResponse<?>> overridden = Mono.from(client.exchange(request(server, 3000), null, null,
                RawRequestOptions.builder().responseTimeout(Duration.ofSeconds(10)).build())).<HttpResponse<?>>map(r -> r).toFuture();
            CompletableFuture<HttpResponse<?>> defaulted = Mono.from(client.exchange(request(server, 5000), null, null)).<HttpResponse<?>>map(r -> r).toFuture();

            // the request that uses the read timeout of the connection times out
            ExecutionException timeout = Assertions.assertThrows(ExecutionException.class, () -> defaulted.get(10, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ReadTimeoutException.class, timeout.getCause());
            Duration timedOutAfter = Duration.ofNanos(System.nanoTime() - start);
            Assertions.assertTrue(timedOutAfter.compareTo(Duration.ofMillis(2500)) < 0, "The default request timed out after " + timedOutAfter);

            // the request with the response timeout of its own gets its response on the same connection
            Assertions.assertEquals(port, body(overridden.get(10, TimeUnit.SECONDS)));

            // the connection is still usable
            Assertions.assertEquals(port, body(exchange(client, server, 0, null)));
        }
    }

    private static ApplicationContext start(boolean tls) {
        return ApplicationContext.run(Map.of(
            "spec.name", "Http2StreamReadTimeoutTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", tls,
            "micronaut.http.client.alpn-modes", "h2",
            "micronaut.http.client.plaintext-mode", "h2c_prior_knowledge",
            "micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms",
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", tls,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
    }

    private static HttpRequest<?> request(EmbeddedServer server, long delay) {
        return HttpRequest.GET(server.getURI().resolve("/http2-stream-read-timeout/slow?delay=" + delay));
    }

    private static HttpResponse<?> exchange(RawHttpClient client, EmbeddedServer server, long delay, Duration responseTimeout) {
        RawRequestOptions options = RawRequestOptions.builder().responseTimeout(responseTimeout).build();
        return Mono.from(client.exchange(request(server, delay), null, null, options)).block();
    }

    private static String body(HttpResponse<?> response) throws Exception {
        try (ByteBodyHttpResponse<?> byteBodyResponse = (ByteBodyHttpResponse<?>) response) {
            Assertions.assertEquals(200, byteBodyResponse.code());
            return byteBodyResponse.byteBody().buffer().get().toString(StandardCharsets.UTF_8);
        }
    }

    @Controller("/http2-stream-read-timeout")
    @Requires(property = "spec.name", value = "Http2StreamReadTimeoutTest")
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay, HttpRequest<?> request) {
            // the port of the client identifies its connection
            String port = String.valueOf(request.getRemoteAddress().getPort());
            return Mono.delay(Duration.ofMillis(delay)).thenReturn(port);
        }
    }
}
