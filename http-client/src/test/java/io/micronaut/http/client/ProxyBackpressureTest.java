package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.util.Map.entry;

public class ProxyBackpressureTest {
    private static final int CHUNK_SIZE = 256 * 1024;
    private static final int TOTAL_CHUNKS = 32;

    @ParameterizedTest
    @CsvSource({
        "false,1,/large",
        "true,1,/large",
        "true,2,/large",
        "true,3,/large",
        "false,1,/proxy",
        "true,1,/proxy",
        "true,2,/proxy",
        "true,3,/proxy",
    })
    public void backpressure(boolean ssl, int version, String endpoint) throws InterruptedException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.ofEntries(
            entry("spec.name", "ProxyBackpressureTest"),
            entry("micronaut.http.client.ssl.insecure-trust-all-certificates", ssl),
            entry("micronaut.http.client.alpn-modes", version == 2 ? "h2" : version == 3 ? "h3" : "http/1.1"),
            entry("micronaut.http.client.read-timeout", "120"),
            entry("micronaut.http.client.http2.initial-window-size", TOTAL_CHUNKS * CHUNK_SIZE),
            entry("micronaut.server.http-version", ssl ? "2.0" : "1.1"),
            entry("micronaut.server.ssl.enabled", ssl),
            entry("micronaut.server.ssl.build-self-signed", true),
            entry("micronaut.server.netty.listeners.main.family", version == 3 ? "quic" : "tcp"),
            entry("micronaut.server.netty.listeners.main.ssl", ssl),
            entry("micronaut.server.netty.listeners.main.port", 0)
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, server.getURI())) {

            Ctrl ctrl = ctx.getBean(Ctrl.class);
            var subscriber = new Subscriber<ByteBuffer<?>>() {
                volatile Subscription subscription;
                volatile long received = 0;
                volatile boolean complete = false;
                Throwable error;

                @Override
                public void onSubscribe(Subscription s) {
                    subscription = s;
                }

                @Override
                public void onNext(ByteBuffer<?> byteBuffer) {
                    received += byteBuffer.readableBytes();
                }

                @Override
                public void onError(Throwable t) {
                    error = t;
                    complete = true;
                }

                @Override
                public void onComplete() {
                    complete = true;
                }
            };
            Flux.from(client.dataStream(HttpRequest.GET(endpoint))).subscribe(subscriber);

            Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> subscriber.subscription != null);
            subscriber.subscription.request(1);
            Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> subscriber.received > 1024);
            Assertions.assertFalse(
                subscriber.complete,
                "response completed despite only one downstream item being requested: " +
                    state(ssl, version, endpoint, subscriber.received, ctrl.emitted, ctrl.emittedChunks)
            );

            subscriber.subscription.request(Long.MAX_VALUE);
            Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> Assertions.assertTrue(
                subscriber.complete,
                "response did not complete after downstream requested the rest: " +
                    state(ssl, version, endpoint, subscriber.received, ctrl.emitted, ctrl.emittedChunks)
            ));
            if (subscriber.error != null) {
                Assertions.fail(subscriber.error);
            }
            Assertions.assertEquals(TOTAL_CHUNKS * CHUNK_SIZE, subscriber.received);
            Assertions.assertEquals(TOTAL_CHUNKS * CHUNK_SIZE, ctrl.emitted);
        }
    }

    private static String state(boolean ssl, int version, String endpoint, long received, long emitted, int emittedChunks) {
        return "ssl=" + ssl + ", version=" + version + ", endpoint=" + endpoint +
            ", received=" + received + ", emitted=" + emitted + ", emittedChunks=" + emittedChunks;
    }

    @Controller
    @Requires(property = "spec.name", value = "ProxyBackpressureTest")
    static class Ctrl {
        volatile long emitted = 0;
        volatile int emittedChunks = 0;

        @Get("/large")
        Publisher<byte[]> large() {
            return Flux.range(0, TOTAL_CHUNKS)
                .map(i -> {
                    var arr = new byte[CHUNK_SIZE];
                    ThreadLocalRandom.current().nextBytes(arr);
                    return arr;
                })
                .doOnNext(it -> {
                    emitted += it.length;
                    emittedChunks++;
                });
        }
    }

    @ServerFilter("/proxy")
    @Requires(property = "spec.name", value = "ProxyBackpressureTest")
    static class Filt {
        @Inject
        ProxyHttpClient proxy;
        @Inject
        EmbeddedServer server;

        @RequestFilter
        Publisher<MutableHttpResponse<?>> proxy() {
            return proxy.proxy(HttpRequest.GET(server.getURI() + "/large"));
        }
    }
}
