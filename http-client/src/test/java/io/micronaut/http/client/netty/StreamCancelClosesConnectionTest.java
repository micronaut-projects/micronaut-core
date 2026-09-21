/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A streamed response body that is abandoned before it ends is drained only up to a limit: the
 * upstream may never end, so beyond the limit the connection is closed (HTTP/1) or the stream
 * reset (HTTP/2).
 */
class StreamCancelClosesConnectionTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void cancellingAnEndlessStreamCancelsTheServerPublisher(int version) throws InterruptedException {
        boolean h2 = version == 2;
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "StreamCancelClosesConnectionTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", h2,
            "micronaut.http.client.alpn-modes", h2 ? "h2" : "http/1.1",
            "micronaut.server.http-version", h2 ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", h2,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, server.getURI())) {

            byte[] first = Flux.from(client.dataStream(HttpRequest.GET("/endless")))
                .map(ByteBuffer::toByteArray)
                .blockFirst();
            Assertions.assertNotNull(first);

            Assertions.assertTrue(ctx.getBean(Events.class).cancelled.await(10, TimeUnit.SECONDS),
                "The server publisher was not cancelled, the client kept reading the abandoned response");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void cancellingASlowEndlessStreamCancelsTheServerPublisher(int version) throws InterruptedException {
        boolean h2 = version == 2;
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "StreamCancelClosesConnectionTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", h2,
            "micronaut.http.client.alpn-modes", h2 ? "h2" : "http/1.1",
            "micronaut.server.http-version", h2 ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", h2,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, server.getURI())) {

            byte[] first = Flux.from(client.dataStream(HttpRequest.GET("/slow-endless")))
                .map(ByteBuffer::toByteArray)
                .blockFirst();
            Assertions.assertNotNull(first);

            // a few bytes at a time never reach the byte limit, the time limit stops the draining
            Assertions.assertTrue(ctx.getBean(Events.class).slowCancelled.await(20, TimeUnit.SECONDS),
                "The server publisher was not cancelled, the client kept reading the abandoned response");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "StreamCancelClosesConnectionTest")
    static class Events {
        final CountDownLatch cancelled = new CountDownLatch(1);
        final CountDownLatch slowCancelled = new CountDownLatch(1);
    }

    @Controller
    @Requires(property = "spec.name", value = "StreamCancelClosesConnectionTest")
    static class Ctrl {
        private final Events events;

        Ctrl(Events events) {
            this.events = events;
        }

        @Get(value = "/slow-endless", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> slowEndless() {
            return Flux.interval(Duration.ofMillis(100))
                .map(i -> new byte[16])
                .doOnCancel(events.slowCancelled::countDown);
        }

        @Get(value = "/endless", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> endless() {
            return Flux.<byte[]>generate(sink -> sink.next(new byte[8192]))
                .doOnCancel(events.cancelled::countDown);
        }
    }
}
