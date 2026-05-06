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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ProxyBackpressureTest {
    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int TOTAL_CHUNKS = 128;

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
    @Execution(ExecutionMode.CONCURRENT)
    public void backpressure(boolean ssl, int version, String endpoint) throws InterruptedException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "ProxyBackpressureTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", ssl,
            "micronaut.http.client.alpn-modes", version == 2 ? "h2" : version == 3 ? "h3" : "http/1.1",
            "micronaut.http.client.read-timeout", "120",
            "micronaut.server.http-version", ssl ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", ssl,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.netty.listeners.main.family", version == 3 ? "quic" : "tcp",
            "micronaut.server.netty.listeners.main.ssl", ssl,
            "micronaut.server.netty.listeners.main.port", 0
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
            TimeUnit.SECONDS.sleep(5);
            Assertions.assertTrue(ctrl.emitted < 32 * CHUNK_SIZE);

            subscriber.subscription.request(Long.MAX_VALUE);
            Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> subscriber.complete);
            if (subscriber.error != null) {
                Assertions.fail(subscriber.error);
            }
            Assertions.assertEquals(TOTAL_CHUNKS * CHUNK_SIZE, subscriber.received);
            Assertions.assertEquals(TOTAL_CHUNKS * CHUNK_SIZE, ctrl.emitted);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "ProxyBackpressureTest")
    static class Ctrl {
        volatile long emitted = 0;

        @Get("/large")
        Publisher<byte[]> large() {
            return Flux.range(0, TOTAL_CHUNKS)
                .map(i -> {
                    var arr = new byte[CHUNK_SIZE];
                    ThreadLocalRandom.current().nextBytes(arr);
                    return arr;
                })
                .doOnNext(it -> emitted += it.length);
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
