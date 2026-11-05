package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.RequestTimeouts;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The read timeout of the {@link RequestTimeouts} of a request applies to its HTTP/3 stream only:
 * the other streams of the connection keep the read timeout of the client.
 */
class Http3RequestTimeoutsTest {

    @Test
    void concurrentStreamsWithDifferentReadTimeouts() throws Exception {
        try (ApplicationContext ctx = start();
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            // opens the connection
            client.toBlocking().retrieve(request(0, null));

            // longer than the read timeout of the client
            CompletableFuture<String> longer = Mono.from(client.retrieve(request(1500, Duration.ofSeconds(5)))).toFuture();
            CompletableFuture<String> shorter = Mono.from(client.retrieve(request(3000, Duration.ofMillis(200)))).toFuture();

            assertTimesOutWithin(shorter, Duration.ofMillis(900));
            Assertions.assertEquals("slow", longer.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void theNextStreamHasTheReadTimeoutOfTheClient() throws Exception {
        try (ApplicationContext ctx = start();
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            Assertions.assertEquals("slow", client.toBlocking().retrieve(request(1500, Duration.ofSeconds(5))));

            assertTimesOutWithin(Mono.from(client.retrieve(request(3000, null))).toFuture(), Duration.ofMillis(1700));
        }
    }

    private static void assertTimesOutWithin(CompletableFuture<?> pending, Duration limit) {
        long start = System.nanoTime();
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
        Assertions.assertInstanceOf(ReadTimeoutException.class, failure.getCause(), () -> String.valueOf(failure.getCause()));
        Duration timedOutAfter = Duration.ofNanos(System.nanoTime() - start);
        Assertions.assertTrue(timedOutAfter.compareTo(limit) < 0, "Timed out after " + timedOutAfter);
    }

    private static ApplicationContext start() {
        return ApplicationContext.run(Map.of(
            "spec.name", "Http3RequestTimeoutsTest",
            "micronaut.server.ssl.enabled", true,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.netty.listeners.a.family", "QUIC",
            "micronaut.server.netty.listeners.a.port", -1,
            "micronaut.http.client.alpn-modes", "h3",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", true,
            "micronaut.http.client.read-timeout", "1s"
        ));
    }

    private static HttpRequest<?> request(long delay, Duration readTimeout) {
        MutableHttpRequest<?> request = HttpRequest.GET("/http3-request-timeouts/slow?delay=" + delay);
        if (readTimeout != null) {
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(readTimeout).build());
        }
        return request;
    }

    @Controller("/http3-request-timeouts")
    @Requires(property = "spec.name", value = "Http3RequestTimeoutsTest")
    static class SlowController {
        // not the remote address, as RequestTimeoutsTest does: it can't be read over HTTP/3
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn("slow");
        }
    }
}
