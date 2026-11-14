package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A raw exchange with {@link RawRequestOptions#isCloseOnDiscard()} closes the connection as soon as
 * its response body is discarded, instead of draining an endless body for a while.
 */
class RawCloseOnDiscardTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void discardingTheBodyClosesTheConnectionAtOnce(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            StreamController controller = ctx.getBean(StreamController.class);
            RawRequestOptions options = RawRequestOptions.builder().closeOnDiscard(true).build();
            ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
                HttpRequest.GET(server.getURI().resolve("/raw-close-on-discard/endless")), null, null, options)).block();
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(controller.started.await(5, TimeUnit.SECONDS));
            long start = System.nanoTime();
            response.close();
            // the client would otherwise drain the stream for 5 seconds before it disconnects
            Assertions.assertTrue(controller.cancelled.await(2500, TimeUnit.MILLISECONDS));
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(2500)) < 0);
        }
    }

    @org.junit.jupiter.api.Test
    void theOptionIsPartOfTheOptions() {
        RawRequestOptions options = RawRequestOptions.builder().closeOnDiscard(true).build();
        Assertions.assertTrue(options.isCloseOnDiscard());
        Assertions.assertFalse(RawRequestOptions.getDefault().isCloseOnDiscard());
        Assertions.assertEquals(options, options.toBuilder().build());
        Assertions.assertNotEquals(options, RawRequestOptions.getDefault());
        Assertions.assertTrue(options.toString().contains("closeOnDiscard=true"));
    }

    private static ApplicationContext start(int version) {
        boolean ssl = version == 2;
        return ApplicationContext.run(Map.of(
            "spec.name", "RawCloseOnDiscardTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", ssl,
            "micronaut.http.client.alpn-modes", ssl ? "h2" : "http/1.1",
            "micronaut.server.http-version", ssl ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", ssl,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
    }

    @Singleton
    @Controller("/raw-close-on-discard")
    @Requires(property = "spec.name", value = "RawCloseOnDiscardTest")
    static class StreamController {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);

        @Get(value = "/endless", produces = MediaType.TEXT_PLAIN)
        Flux<String> endless() {
            return Flux.interval(Duration.ofMillis(50))
                .map(i -> "tick\n")
                .doOnNext(t -> started.countDown())
                .doOnCancel(cancelled::countDown);
        }
    }
}
