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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * The life of one exchange on a connection, from the request head to the end of the response:
 * the body held back for {@code 100 Continue}, cancellation, protocol switches, failures of the
 * request pipeline and the outcome reported to the load balancer. Each exchange reports exactly
 * one outcome, and gives the connection back or closes it.
 */
class ClientExchangeLifecycleTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final String OK = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aLateContinueAfterTheFallbackDoesNotSendTheBodyAgain(boolean streamed) throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "200ms"))) {
            CompletableFuture<HttpResponse<?>> pending = client.expectContinue(streamed);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("payload");

            connection.write("HTTP/1.1 100 Continue\r\n\r\n" + OK);

            assertResponse(pending, 200, "ok");
            Assertions.assertEquals(1, occurrences(connection.received(), "payload"), connection.received());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFinalResponseBeforeTheFallbackStopsTheTimer(boolean streamed) throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "300ms"))) {
            CompletableFuture<HttpResponse<?>> pending = client.expectContinue(streamed);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");

            connection.write("HTTP/1.1 417 Expectation Failed\r\nContent-Length: 4\r\n\r\nnope");

            assertResponse(pending, 417, "nope");
            Assertions.assertTrue(connection.awaitClosed(), "The client kept the connection open");
            Thread.sleep(500);
            Assertions.assertFalse(connection.received().contains("payload"), connection.received());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);
        }
    }

    @Test
    void cancellingBeforeTheResponseClosesTheConnection() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/slow"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");

            pending.cancel(true);

            Assertions.assertTrue(connection.awaitClosed(), "The client kept the connection of the cancelled exchange open");
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED);

            CompletableFuture<HttpResponse<?>> next = client.raw(HttpRequest.GET("/next"), null);
            upstream.connection(1).awaitReceived("\r\n\r\n");
            upstream.connection(1).write(OK);
            assertResponse(next, 200, "ok");
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED, LoadBalancer.Outcome.SUCCESS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellingWhileTheBodyIsHeldForContinueClosesTheConnection(boolean streamed) throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "10s"))) {
            CompletableFuture<HttpResponse<?>> pending = client.expectContinue(streamed);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");

            pending.cancel(true);

            Assertions.assertTrue(connection.awaitClosed(), "The client kept the connection of the cancelled exchange open");
            Assertions.assertFalse(connection.received().contains("payload"), connection.received());
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED);
            client.awaitNoLiveRequest();
        }
    }

    @Test
    void cancellingAfterTheResponseKeepsTheConnection() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/first"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.write(OK);
            assertResponse(pending, 200, "ok");
            pending.cancel(true);
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);

            CompletableFuture<HttpResponse<?>> next = client.raw(HttpRequest.GET("/second"), null);
            connection.awaitReceived("GET /second");
            connection.write(OK);
            assertResponse(next, 200, "ok");
            Assertions.assertEquals(1, upstream.connectionCount());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.SUCCESS);
        }
    }

    @ParameterizedTest
    @CsvSource({"200, SUCCESS", "404, SUCCESS", "503, SERVER_ERROR"})
    void theStatusOfTheResponseIsReportedOnce(int status, LoadBalancer.Outcome outcome) throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/status"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.write("HTTP/1.1 " + status + " Whatever\r\nContent-Length: 2\r\n\r\nok");
            assertResponse(pending, status, "ok");
            client.awaitOutcomes(outcome);
        }
    }

    @Test
    void aResponseDiscardedBeforeItsBodyEndedIsReportedAsResponded() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/long"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nstart");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
            }
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);
        }
    }

    @Test
    void aConnectionClosedBeforeTheResponseIsReportedAsReset() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/reset"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.close();

            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ResponseClosedException.class, e.getCause());
            client.awaitOutcomes(LoadBalancer.Outcome.RESET);
            client.awaitNoLiveRequest();
            // a request on a new connection is not sent again
            Assertions.assertEquals(1, upstream.connectionCount());
        }
    }

    @Test
    void aBodyThatEndsEarlyIsReportedAsReset() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/truncated"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nstart");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                CompletableFuture<?> body = response.byteBody().buffer();
                connection.close();
                Assertions.assertThrows(ExecutionException.class, () -> body.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            client.awaitOutcomes(LoadBalancer.Outcome.RESET);
        }
    }

    @Test
    void aReadTimeoutIsReportedAsTimeout() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.read-timeout", "300ms"))) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("/never"), null);
            upstream.connection(0).awaitReceived("\r\n\r\n");

            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ReadTimeoutException.class, e.getCause());
            client.awaitOutcomes(LoadBalancer.Outcome.TIMEOUT);
            Assertions.assertTrue(upstream.connection(0).awaitClosed(), "The client kept the timed out connection open");
        }
    }

    @Test
    void aStaleConnectionIsRetriedAndOnlyTheRetryIsReported() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> first = client.raw(HttpRequest.PUT("/first", null).contentType(MediaType.TEXT_PLAIN_TYPE), text("one"));
            Connection connection = upstream.connection(0);
            connection.awaitReceived("one");
            connection.write(OK);
            assertResponse(first, 200, "ok");
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);
            Thread.sleep(100);

            CompletableFuture<HttpResponse<?>> second = client.raw(HttpRequest.PUT("/second", null).contentType(MediaType.TEXT_PLAIN_TYPE), text("two"));
            connection.awaitReceived("two");
            // the server closed the keep-alive connection instead of answering
            connection.close();

            Connection retry = upstream.connection(1);
            retry.awaitReceived("two");
            retry.write(OK);
            assertResponse(second, 200, "ok");
            Assertions.assertTrue(retry.received().startsWith("PUT /second HTTP/1.1\r\n"), retry.received());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.SUCCESS);
            client.awaitNoLiveRequest();
        }
    }

    @Test
    void aStaleConnectionIsNotRetriedForARequestThatExpectsContinue() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "10s"))) {
            CompletableFuture<HttpResponse<?>> first = client.raw(HttpRequest.GET("/first"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.write(OK);
            assertResponse(first, 200, "ok");
            Thread.sleep(100);

            MutableHttpRequest<?> request = HttpRequest.create(HttpMethod.PUT, "/second")
                .header(HttpHeaders.EXPECT, "100-continue")
                .contentType(MediaType.TEXT_PLAIN_TYPE);
            CompletableFuture<HttpResponse<?>> second = client.raw(request, body(false));
            connection.awaitReceived("PUT /second");
            connection.awaitReceived("\r\n\r\n", 2);
            connection.close();

            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientException.class, e.getCause());
            Assertions.assertEquals(1, upstream.connectionCount());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.RESET);
            client.awaitNoLiveRequest();
        }
    }

    @Test
    void aSwitchedConnectionIsNotCountedAndNotReused() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/upgrade")
                .header(HttpHeaders.CONNECTION, "Upgrade")
                .header(HttpHeaders.UPGRADE, "echo");
            CompletableFuture<HttpResponse<?>> pending = client.raw(request, null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            Assertions.assertTrue(connection.received().toLowerCase(java.util.Locale.ROOT).contains("connection: upgrade"), connection.received());

            connection.write("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n");

            HttpResponse<?> response = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertEquals(101, response.code());
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED);
            ((AutoCloseable) response).close();
            Assertions.assertTrue(connection.awaitClosed(), "The switched connection was not closed with its response");

            CompletableFuture<HttpResponse<?>> next = client.raw(HttpRequest.GET("/next"), null);
            upstream.connection(1).awaitReceived("\r\n\r\n");
            upstream.connection(1).write(OK);
            assertResponse(next, 200, "ok");
            client.awaitNoLiveRequest();
        }
    }

    @Test
    void aSwitchToAnotherProtocolFailsAndClosesTheConnection() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/upgrade")
                .header(HttpHeaders.CONNECTION, "Upgrade")
                .header(HttpHeaders.UPGRADE, "echo");
            CompletableFuture<HttpResponse<?>> pending = client.raw(request, null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");

            connection.write("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: other\r\n\r\n");

            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientException.class, e.getCause());
            Assertions.assertTrue(e.getCause().getMessage().contains("'other'"), e.getCause().getMessage());
            Assertions.assertTrue(connection.awaitClosed(), "The client kept the connection open");
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED);
            client.awaitNoLiveRequest();
        }
    }

    @Test
    void aSwitchDropsABodyHeldForContinue() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "10s"))) {
            MutableHttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "/upgrade")
                .header(HttpHeaders.EXPECT, "100-continue")
                .header(HttpHeaders.CONNECTION, "Upgrade")
                .header(HttpHeaders.UPGRADE, "echo")
                .contentType(MediaType.TEXT_PLAIN_TYPE);
            CompletableFuture<HttpResponse<?>> pending = client.raw(request, body(true));
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");

            connection.write("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n");

            HttpResponse<?> response = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertEquals(101, response.code());
            Thread.sleep(200);
            Assertions.assertFalse(connection.received().contains("payload"), connection.received());
            ((AutoCloseable) response).close();
            Assertions.assertTrue(connection.awaitClosed(), "The switched connection was not closed with its response");
            client.awaitOutcomes(LoadBalancer.Outcome.CANCELLED);
        }
    }

    @Test
    void aCustomizerFailureEndsTheExchangeOnce() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = ApplicationContext.run()) {
            List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>();
            AtomicBoolean failNext = new AtomicBoolean();
            NettyClientCustomizer customizer = new NettyClientCustomizer() {
                @Override
                public NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                    return this;
                }

                @Override
                public void onRequestPipelineBuilt() {
                    if (failNext.compareAndSet(true, false)) {
                        throw new IllegalStateException("customizer failure");
                    }
                }
            };
            try (DefaultHttpClient client = DefaultHttpClient.builder()
                .loadBalancer(loadBalancer(upstream, outcomes))
                .configuration(ctx.getBean(HttpClientConfiguration.class))
                .clientCustomizer(customizer)
                .build()) {
                CompletableFuture<HttpResponse<?>> first = Mono.<HttpResponse<?>>from(client.exchange(HttpRequest.GET("/first"), (CloseableByteBody) null, null)).toFuture();
                upstream.connection(0).awaitReceived("\r\n\r\n");
                upstream.connection(0).write(OK);
                assertResponse(first, 200, "ok");
                await(() -> outcomes.equals(List.of(LoadBalancer.Outcome.SUCCESS)), outcomes::toString);

                failNext.set(true);
                CompletableFuture<HttpResponse<?>> second = Mono.<HttpResponse<?>>from(client.exchange(HttpRequest.GET("/second"), (CloseableByteBody) null, null)).toFuture();
                ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                Assertions.assertEquals("customizer failure", e.getCause().getMessage());
                Assertions.assertTrue(upstream.connection(0).awaitClosed(), "The client kept the connection open");
                await(() -> client.connectionManager().liveRequestCount() == 0, () -> "live requests");
                Thread.sleep(100);
                Assertions.assertEquals(2, outcomes.size(), outcomes.toString());
                Assertions.assertEquals(LoadBalancer.Outcome.SUCCESS, outcomes.get(0));
                // the pipeline failed the exchange before anything was sent: nothing is counted against the instance
                Assertions.assertEquals(LoadBalancer.Outcome.CANCELLED, outcomes.get(1));
            }
        }
    }

    @Test
    void anUpgradeOnAnHttp2ConnectionFailsBeforeItIsSent() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.http-version", "2.0"));
             ApplicationContext ctx = ApplicationContext.run()) {
            List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>();
            ServiceInstance instance = ServiceInstance.of("lifecycle", server.getURI());
            LoadBalancer loadBalancer = new LoadBalancer() {
                @Override
                public Publisher<ServiceInstance> select(Object discriminator) {
                    return Publishers.just(instance);
                }

                @Override
                public void report(ServiceInstance serviceInstance, LoadBalancer.Outcome outcome) {
                    outcomes.add(outcome);
                }
            };
            try (DefaultHttpClient client = DefaultHttpClient.builder()
                .loadBalancer(loadBalancer)
                .explicitHttpVersion(HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_2_0))
                .configuration(ctx.getBean(HttpClientConfiguration.class))
                .build()) {
                // the first exchange switches the connection to HTTP/2
                HttpResponse<?> first = Mono.<HttpResponse<?>>from(client.exchange(HttpRequest.GET("/missing"), (CloseableByteBody) null, null, RawRequestOptions.proxy()))
                    .toFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                ((AutoCloseable) first).close();
                await(() -> outcomes.size() == 1, outcomes::toString);

                MutableHttpRequest<?> request = HttpRequest.GET("/upgrade")
                    .header(HttpHeaders.CONNECTION, "Upgrade")
                    .header(HttpHeaders.UPGRADE, "echo");
                CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(client.exchange(request, (CloseableByteBody) null, null, RawRequestOptions.proxy())).toFuture();
                ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                Assertions.assertInstanceOf(HttpClientException.class, e.getCause());
                Assertions.assertTrue(e.getCause().getMessage().contains("needs an HTTP/1.1 connection"), e.getCause().getMessage());
                await(() -> outcomes.size() == 2, outcomes::toString);
                Thread.sleep(100);
                Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.CANCELLED), outcomes);
                await(() -> client.connectionManager().liveRequestCount() == 0, () -> "A request is still live");
            }
        }
    }

    @Test
    void aBodyHeldForContinueIsSentOnceByTheFallbackAndNotAfterAFailure() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of("micronaut.http.client.expect-continue-timeout", "300ms"))) {
            CompletableFuture<HttpResponse<?>> pending = client.expectContinue(false);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("\r\n\r\n");
            connection.close();

            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ResponseClosedException.class, e.getCause());
            client.awaitOutcomes(LoadBalancer.Outcome.RESET);
            Thread.sleep(500);
            client.awaitNoLiveRequest();
            Assertions.assertEquals(1, upstream.connectionCount());
        }
    }

    @Test
    void anErrorResponseIsMappedByTheRegularClient() throws Exception {
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream, Map.of())) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(client.client.exchange(HttpRequest.GET("/missing"), String.class)).toFuture();
            upstream.connection(0).awaitReceived("\r\n\r\n");
            upstream.connection(0).write("HTTP/1.1 404 Not Found\r\nContent-Length: 4\r\n\r\nnope");
            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientResponseException.class, e.getCause());
            client.awaitOutcomes(LoadBalancer.Outcome.SUCCESS);
        }
    }

    private static CloseableByteBody text(String text) {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(text.getBytes(StandardCharsets.UTF_8));
    }

    private static CloseableByteBody body(boolean streamed) {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        return streamed
            ? factory.adapt(Flux.just("payload").map(text -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8)))
            : factory.adapt("payload".getBytes(StandardCharsets.UTF_8));
    }

    private static void assertResponse(CompletableFuture<HttpResponse<?>> pending, int code, String body) throws Exception {
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(code, response.code());
            Assertions.assertEquals(body, response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
        }
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            Assertions.assertTrue(System.nanoTime() < deadline, message);
            Thread.sleep(10);
        }
    }

    private static LoadBalancer loadBalancer(Upstream upstream, List<LoadBalancer.Outcome> outcomes) {
        ServiceInstance instance = ServiceInstance.of("lifecycle", URI.create(upstream.uri("")));
        return new LoadBalancer() {
            @Override
            public Publisher<ServiceInstance> select(Object discriminator) {
                return Publishers.just(instance);
            }

            @Override
            public void report(ServiceInstance serviceInstance, LoadBalancer.Outcome outcome) {
                outcomes.add(outcome);
            }
        };
    }

    /**
     * A load balanced client that records the outcomes it reports.
     */
    private static final class Client implements AutoCloseable {
        final List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>();
        final ApplicationContext ctx;
        final DefaultHttpClient client;
        final Upstream upstream;

        Client(Upstream upstream, Map<String, Object> properties) {
            this.upstream = upstream;
            ctx = ApplicationContext.run(properties);
            client = DefaultHttpClient.builder()
                .loadBalancer(loadBalancer(upstream, outcomes))
                .configuration(ctx.getBean(HttpClientConfiguration.class))
                .build();
        }

        CompletableFuture<HttpResponse<?>> raw(MutableHttpRequest<?> request, CloseableByteBody body) {
            return Mono.<HttpResponse<?>>from(client.exchange(request, body, null, RawRequestOptions.proxy())).toFuture();
        }

        CompletableFuture<HttpResponse<?>> expectContinue(boolean streamed) {
            return raw(HttpRequest.create(HttpMethod.POST, "/expect")
                .header(HttpHeaders.EXPECT, "100-continue")
                .contentType(MediaType.TEXT_PLAIN_TYPE), body(streamed));
        }

        void awaitOutcomes(LoadBalancer.Outcome... expected) throws InterruptedException {
            List<LoadBalancer.Outcome> list = List.of(expected);
            await(() -> outcomes.size() >= list.size(), () -> "Expected " + list + ", reported " + outcomes);
            // a second report of the same exchange would arrive right after the first
            Thread.sleep(100);
            Assertions.assertEquals(list, outcomes);
        }

        void awaitNoLiveRequest() throws InterruptedException {
            await(() -> client.connectionManager().liveRequestCount() == 0, () -> "A request is still live");
        }

        @Override
        public void close() {
            client.close();
            ctx.close();
        }
    }

    /**
     * Accepts connections and records what the client sends on each.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<Connection> connections = new CopyOnWriteArrayList<>();

        Upstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::accept, "exchange-lifecycle-upstream");
            thread.setDaemon(true);
            thread.start();
        }

        String uri(String path) {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
        }

        int connectionCount() {
            return connections.size();
        }

        Connection connection(int index) throws InterruptedException {
            await(() -> connections.size() > index, () -> "The client did not open connection " + index);
            return connections.get(index);
        }

        private void accept() {
            try {
                while (true) {
                    Connection connection = new Connection(serverSocket.accept());
                    connections.add(connection);
                    Thread thread = new Thread(connection::read, "exchange-lifecycle-connection");
                    thread.setDaemon(true);
                    thread.start();
                }
            } catch (IOException ignored) {
                // closed by the test
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            for (Connection connection : connections) {
                connection.close();
            }
        }
    }

    private static final class Connection {
        private final Socket socket;
        private final StringBuffer received = new StringBuffer();
        private final CountDownLatch closed = new CountDownLatch(1);

        Connection(Socket socket) {
            this.socket = socket;
        }

        String received() {
            return received.toString();
        }

        private void read() {
            try (Socket s = socket) {
                InputStream in = s.getInputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    received.append(new String(buffer, 0, n, StandardCharsets.ISO_8859_1));
                }
            } catch (IOException ignored) {
                // closed by the test
            } finally {
                closed.countDown();
            }
        }

        void awaitReceived(String text) throws InterruptedException {
            awaitReceived(text, 1);
        }

        void awaitReceived(String text, int times) throws InterruptedException {
            await(() -> occurrences(received(), text) >= times, () -> "Did not receive " + text.strip() + ", received: " + received());
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        void write(String text) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write(text.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        void close() throws IOException {
            socket.close();
        }
    }
}
