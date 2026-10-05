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

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The order of the events of one exchange, driven one by one on an embedded channel: callbacks
 * that run synchronously or are queued on the event loop, subscribers that come before or after
 * the response, cancellation before and after each hand-off, and late callbacks of an exchange
 * after its connection was reused. Each case checks that the flow completes once, every resource
 * is released once, one outcome is reported and the connection can take the next request.
 */
class ClientExchangeOrderingTest {
    private static final Duration CONTINUE_TIMEOUT = Duration.ofSeconds(1);

    private final List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>();
    private final ServiceInstance instance = ServiceInstance.of("ordering", URI.create("http://localhost"));
    private final LoadBalancer loadBalancer = new LoadBalancer() {
        @Override
        public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Flux.just(instance);
        }

        @Override
        public void report(ServiceInstance serviceInstance, LoadBalancer.Outcome outcome) {
            outcomes.add(outcome);
        }
    };
    private DefaultHttpClient defaultClient;
    private NettyHttpClient client;
    private EmbeddedChannel channel;
    private Http1ResponseHandler responseHandler;

    @BeforeEach
    void setUp() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setExpectContinueTimeout(CONTINUE_TIMEOUT);
        defaultClient = DefaultHttpClient.builder().uri(URI.create("http://localhost")).configuration(configuration).build();
        client = defaultClient.getNettyHttpClient();
        responseHandler = new Http1ResponseHandler();
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, responseHandler);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
        defaultClient.close();
    }

    @Test
    void continueSendsTheHeldBodyAndTheTimerDoesNotSendItAgain() {
        ByteBuf payload = payload();
        Attempt attempt = start(expectContinue(), buffered(payload), false);
        Assertions.assertEquals(ClientExchange.Phase.WAITING_FOR_CONTINUE, attempt.exchange.phase());
        Assertions.assertEquals(List.of("head"), written());

        channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        Assertions.assertEquals(ClientExchange.Phase.SENDING, attempt.exchange.phase());
        Assertions.assertEquals(List.of("last:payload"), written());

        fireTimers();
        Assertions.assertEquals(List.of(), written());

        respond("ok");
        attempt.assertResponse("ok");
        assertFinished(attempt, false, LoadBalancer.Outcome.SUCCESS);
        Assertions.assertEquals(1, payload.refCnt());
        payload.release();
        assertConnectionUsable();
    }

    @Test
    void theTimerSendsTheHeldBodyAndALateContinueDoesNotSendItAgain() {
        Attempt attempt = start(expectContinue(), buffered(payload()), false);
        Assertions.assertEquals(List.of("head"), written());

        fireTimers();
        Assertions.assertEquals(List.of("last:payload"), written());
        Assertions.assertEquals(ClientExchange.Phase.SENDING, attempt.exchange.phase());

        channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        Assertions.assertEquals(List.of(), written());

        respond("ok");
        attempt.assertResponse("ok");
        assertFinished(attempt, false, LoadBalancer.Outcome.SUCCESS);
        assertConnectionUsable();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFinalResponseDropsTheHeldBodyAndStopsTheTimer(boolean streamed) {
        ByteBuf payload = payload();
        TestPublisher publisher = new TestPublisher();
        Attempt attempt = start(expectContinue(), streamed ? streamed(publisher) : buffered(payload), false);

        channel.writeInbound(response(HttpResponseStatus.EXPECTATION_FAILED, "nope"));
        attempt.assertResponse("nope");
        if (streamed) {
            Assertions.assertFalse(publisher.subscribed.get() && !publisher.cancelled.get(), "The held stream is still subscribed");
        } else {
            Assertions.assertEquals(1, payload.refCnt(), "The held body was not released");
        }

        fireTimers();
        Assertions.assertEquals(List.of("head"), written());
        assertFinished(attempt, true, LoadBalancer.Outcome.SUCCESS);
        payload.release();
    }

    @Test
    void aCancellationIsQueuedAndClosesTheConnectionBeforeTheResponse() {
        ByteBuf payload = payload();
        Attempt attempt = start(expectContinue(), buffered(payload), false);

        attempt.sink.cancel();
        // the cancellation of the flow may come from any thread: it waits for the event loop
        Assertions.assertTrue(channel.isActive());
        Assertions.assertEquals(ClientExchange.Phase.WAITING_FOR_CONTINUE, attempt.exchange.phase());

        channel.runPendingTasks();
        Assertions.assertFalse(channel.isActive());
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        Assertions.assertEquals(0, attempt.completions.get());
        Assertions.assertEquals(1, payload.refCnt(), "The held body was not released");
        fireTimers();
        assertFinished(attempt, true, LoadBalancer.Outcome.CANCELLED);
        payload.release();
    }

    @Test
    void aResponseThatArrivesBeforeTheQueuedCancellationIsClosedAndTheConnectionKept() {
        Attempt attempt = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        attempt.sink.cancel();

        // the response is read before the queued cancellation runs
        channel.writeInbound(response(HttpResponseStatus.OK, "ok"));
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        Assertions.assertEquals(0, attempt.completions.get());

        channel.runPendingTasks();
        Assertions.assertTrue(channel.isActive(), "A cancellation after the response closed the connection");
        assertFinished(attempt, false, LoadBalancer.Outcome.CANCELLED);
        assertConnectionUsable();
    }

    @Test
    void aCancellationAfterTheResponseKeepsTheConnection() {
        Attempt attempt = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        respond("ok");
        attempt.assertResponse("ok");

        attempt.sink.cancel();
        channel.runPendingTasks();
        Assertions.assertTrue(channel.isActive());
        assertFinished(attempt, false, LoadBalancer.Outcome.SUCCESS);
        assertConnectionUsable();
    }

    @Test
    void aSubscriberThatClosesTheResponseSynchronouslyReportsItOnce() {
        Attempt attempt = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        attempt.closeOnComplete = true;

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers().set(HttpHeaderNames.CONTENT_LENGTH, 10);
        channel.writeInbound(head, new DefaultHttpContent(Unpooled.copiedBuffer("start", StandardCharsets.UTF_8)));
        Assertions.assertEquals(1, attempt.completions.get());
        // the caller let the response go before its body ended: the instance responded
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS), outcomes);
        Assertions.assertEquals(ClientExchange.Phase.RECEIVING, attempt.exchange.phase());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("_rest", StandardCharsets.UTF_8)));
        assertFinished(attempt, false, LoadBalancer.Outcome.SUCCESS);
        assertConnectionUsable();
    }

    @Test
    void aSubscriberThatComesAfterTheResponseGetsItOnce() {
        Attempt attempt = start(get(), buffered(Unpooled.EMPTY_BUFFER), false, false);
        respond("ok");
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());

        attempt.subscribe();
        attempt.assertResponse("ok");
        assertFinished(attempt, false, LoadBalancer.Outcome.SUCCESS);
        assertConnectionUsable();
    }

    @Test
    void aCustomizerThatEndsTheExchangeSynchronouslyReleasesTheHeldBodyOnce() {
        ByteBuf payload = payload();
        Attempt attempt = start(expectContinue(), buffered(payload), false, true,
            () -> channel.pipeline().fireExceptionCaught(new IOException("customizer")));
        // the customizer ended the exchange inside start(): nothing is held, no timer is set
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        Assertions.assertEquals(1, attempt.completions.get());
        Assertions.assertNotNull(attempt.failure.get());
        Assertions.assertEquals(1, payload.refCnt(), "The held body was not released once");
        fireTimers();
        Assertions.assertFalse(written().contains("last:payload"));
        // a failure that is not a connection failure is not counted against the instance
        assertFinished(attempt, true, LoadBalancer.Outcome.CANCELLED);
        payload.release();
    }

    @Test
    void aCustomizerThatFailsEndsTheExchangeOnce() {
        ByteBuf payload = payload();
        Attempt attempt = start(expectContinue(), buffered(payload), false, true,
            () -> {
                throw new IllegalStateException("customizer failure");
            });
        Assertions.assertEquals("customizer failure", attempt.failure.get().getMessage());
        Assertions.assertFalse(channel.isActive());
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        Assertions.assertEquals(1, payload.refCnt(), "The held body was not released once");
        fireTimers();
        // the customizer closed the connection: the exchange fails as a closed connection
        assertFinished(attempt, true, LoadBalancer.Outcome.RESET);
        payload.release();
    }

    @Test
    void aStaleConnectionHandsTheReplayBodyAndTheSelectionToTheRetry() {
        ByteBuf payload = payload();
        Attempt attempt = start(put(), buffered(payload), true);
        Assertions.assertEquals(List.of("full:payload"), written());

        // the server had closed the reused connection
        channel.close();
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        NettyHttpClient.StaleConnectionException stale = Assertions.assertInstanceOf(NettyHttpClient.StaleConnectionException.class, attempt.failure.get());
        Assertions.assertNotNull(stale.replayBody);
        Assertions.assertEquals("payload", new String(stale.replayBody.toByteArray(), StandardCharsets.UTF_8));
        // the selection goes on to the retry, unreported
        Assertions.assertEquals(List.of(), outcomes);
        Assertions.assertEquals(1, attempt.handle.releases);
        stale.replayBody.close();
        attempt.selection.release();
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.CANCELLED), outcomes);
        Assertions.assertEquals(1, payload.refCnt());
        payload.release();
    }

    @Test
    void aCancelledExchangeOnAStaleConnectionReleasesTheReplayBody() {
        ByteBuf payload = payload();
        Attempt attempt = start(put(), buffered(payload), true);
        attempt.sink.cancel();
        // the connection closes before the queued cancellation runs
        channel.close();
        channel.runPendingTasks();
        Assertions.assertNull(attempt.failure.get());
        assertFinished(attempt, true, LoadBalancer.Outcome.CANCELLED);
        // the request the channel did not send is released with it
        written();
        Assertions.assertEquals(1, payload.refCnt(), "The replay body was not released");
        payload.release();
    }

    @Test
    void aStreamedBodyGoesOnWhileTheResponseIsReceivedAndStopsWithIt() {
        TestPublisher publisher = new TestPublisher();
        Attempt attempt = start(put(), streamed(publisher), false);
        Assertions.assertEquals(List.of("head"), written());
        Assertions.assertTrue(publisher.subscribed.get());
        publisher.emit("one");
        channel.runPendingTasks();
        Assertions.assertEquals(List.of("chunk:one"), written());

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        channel.writeInbound(head);
        Assertions.assertEquals(ClientExchange.Phase.RECEIVING, attempt.exchange.phase());
        // the upload goes on after the response head
        publisher.emit("two");
        channel.runPendingTasks();
        Assertions.assertEquals(List.of("chunk:two"), written());
        Assertions.assertFalse(publisher.cancelled.get());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", StandardCharsets.UTF_8)));
        attempt.assertResponse("ok");
        // the request was not sent completely: the stream is cancelled and the connection not reused
        Assertions.assertTrue(publisher.cancelled.get());
        assertFinished(attempt, true, LoadBalancer.Outcome.SUCCESS);
    }

    @Test
    void theTimerOfAFinishedExchangeDoesNotSendAfterTheConnectionIsReused() {
        Attempt first = start(expectContinue(), buffered(payload()), false);
        channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        respond("first");
        first.assertResponse("first");
        Assertions.assertEquals(List.of("head", "last:payload"), written());

        Attempt second = start(expectContinue(), buffered(payload()), false);
        Assertions.assertEquals(List.of("head"), written());
        fireTimers();
        // only the timer of the second exchange sends its body
        Assertions.assertEquals(List.of("last:payload"), written());
        respond("second");
        second.assertResponse("second");
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.SUCCESS), outcomes);
        assertConnectionUsable();
    }

    @Test
    void aLateCancellationOfAFinishedExchangeLeavesTheReusedConnectionAlone() {
        Attempt first = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        respond("first");
        first.assertResponse("first");

        Attempt second = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        first.sink.cancel();
        channel.runPendingTasks();
        Assertions.assertTrue(channel.isActive());
        Assertions.assertFalse(second.handle.tainted);
        respond("second");
        second.assertResponse("second");
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS, LoadBalancer.Outcome.SUCCESS), outcomes);
        assertConnectionUsable();
    }

    @Test
    void aBusyConnectionFailsTheExchangeAndReleasesEverything() {
        Attempt first = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        ByteBuf payload = payload();
        // a second exchange on the same connection while the first is still in progress
        Attempt second = start(put(), buffered(payload), true);
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, second.exchange.phase());
        Assertions.assertEquals(1, second.completions.get());
        Assertions.assertTrue(second.failure.get().getMessage().contains("Failed to send the request on the connection"), second.failure.get().getMessage());
        Assertions.assertEquals(1, second.handle.releases);
        Assertions.assertTrue(second.handle.tainted);
        Assertions.assertEquals(1, payload.refCnt(), "The body or its replay copy was not released");
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.CANCELLED), outcomes);
        payload.release();

        respond("first");
        first.assertResponse("first");
    }

    private void assertFinished(Attempt attempt, boolean tainted, LoadBalancer.Outcome... outcome) {
        Assertions.assertEquals(ClientExchange.Phase.FINISHED, attempt.exchange.phase());
        Assertions.assertTrue(attempt.completions.get() <= 1, "The flow completed more than once");
        Assertions.assertEquals(1, attempt.handle.releases, "The pool handle was not released once");
        Assertions.assertEquals(tainted, attempt.handle.tainted);
        Assertions.assertEquals(List.of(outcome), outcomes.subList(outcomes.size() - outcome.length, outcomes.size()));
        Assertions.assertTrue(responseHandler.isIdle());
    }

    /**
     * The next request on the connection gets its response.
     */
    private void assertConnectionUsable() {
        Assertions.assertTrue(channel.isActive());
        written();
        outcomes.clear();
        Attempt next = start(get(), buffered(Unpooled.EMPTY_BUFFER), false);
        Assertions.assertEquals(List.of("full:"), written());
        respond("next");
        next.assertResponse("next");
        assertFinished(next, false, LoadBalancer.Outcome.SUCCESS);
    }

    private void respond(String text) {
        channel.writeInbound(response(HttpResponseStatus.OK, text));
    }

    private static DefaultFullHttpResponse response(HttpResponseStatus status, String text) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(text, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, text.length());
        return response;
    }

    private void fireTimers() {
        channel.advanceTimeBy(CONTINUE_TIMEOUT.toNanos() * 2, TimeUnit.NANOSECONDS);
        channel.runScheduledPendingTasks();
        channel.runPendingTasks();
    }

    /**
     * @return What the exchanges wrote since the last call
     */
    private List<String> written() {
        channel.runPendingTasks();
        List<String> written = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof FullHttpRequest full) {
                written.add("full:" + full.content().toString(StandardCharsets.UTF_8));
            } else if (message instanceof io.netty.handler.codec.http.HttpRequest) {
                written.add("head");
            } else if (message instanceof LastHttpContent last) {
                written.add("last:" + last.content().toString(StandardCharsets.UTF_8));
            } else if (message instanceof HttpContent chunk) {
                written.add("chunk:" + chunk.content().toString(StandardCharsets.UTF_8));
            } else {
                written.add(message.toString());
            }
            ReferenceCountUtil.release(message);
        }
        return written;
    }

    private static ByteBuf payload() {
        ByteBuf buf = Unpooled.copiedBuffer("payload", StandardCharsets.UTF_8);
        // the test keeps a reference, to see that the client releases its own once
        buf.retain();
        return buf;
    }

    private CloseableByteBody buffered(ByteBuf buf) {
        return new NettyByteBodyFactory(channel).adapt(buf);
    }

    private static CloseableByteBody streamed(TestPublisher publisher) {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(publisher);
    }

    private static DefaultHttpRequest expectContinue() {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/expect");
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        return request;
    }

    private static DefaultHttpRequest get() {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/get");
    }

    private static DefaultHttpRequest put() {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/put");
    }

    private Attempt start(DefaultHttpRequest nettyRequest, CloseableByteBody body, boolean allowRetry) {
        return start(nettyRequest, body, allowRetry, true);
    }

    private Attempt start(DefaultHttpRequest nettyRequest, CloseableByteBody body, boolean allowRetry, boolean subscribe) {
        return start(nettyRequest, body, allowRetry, subscribe, () -> {
        });
    }

    private Attempt start(DefaultHttpRequest nettyRequest, CloseableByteBody body, boolean allowRetry, boolean subscribe, Runnable onPipelineBuilt) {
        TestHandle handle = new TestHandle(channel, responseHandler, onPipelineBuilt);
        LoadBalancerSelection selection = new LoadBalancerSelection(loadBalancer, instance);
        DelayedExecutionFlow<NettyClientByteBodyResponse> sink = DelayedExecutionFlow.create();
        HttpRequest<?> request = HttpRequest.create(io.micronaut.http.HttpMethod.parse(nettyRequest.method().name()), "http://localhost" + nettyRequest.uri());
        ExchangePlan plan = ExchangePlan.of(request, nettyRequest, selection, body, allowRetry);
        ClientExchange exchange = new ClientExchange(client, handle, plan, sink);
        Attempt attempt = new Attempt(exchange, handle, selection, sink);
        if (subscribe) {
            attempt.subscribe();
        }
        exchange.start(body);
        return attempt;
    }

    private static final class Attempt {
        final ClientExchange exchange;
        final TestHandle handle;
        final LoadBalancerSelection selection;
        final DelayedExecutionFlow<NettyClientByteBodyResponse> sink;
        final AtomicInteger completions = new AtomicInteger();
        final AtomicReference<@Nullable NettyClientByteBodyResponse> response = new AtomicReference<>();
        final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        boolean closeOnComplete;

        Attempt(ClientExchange exchange, TestHandle handle, LoadBalancerSelection selection, DelayedExecutionFlow<NettyClientByteBodyResponse> sink) {
            this.exchange = exchange;
            this.handle = handle;
            this.selection = selection;
            this.sink = sink;
        }

        void subscribe() {
            sink.onComplete((value, error) -> {
                completions.incrementAndGet();
                failure.set(error);
                if (value != null) {
                    if (closeOnComplete) {
                        value.close();
                    } else {
                        response.set(value);
                    }
                }
            });
        }

        void assertResponse(String body) {
            Assertions.assertEquals(1, completions.get());
            NettyClientByteBodyResponse value = response.get();
            Assertions.assertNotNull(value, () -> "No response: " + failure.get());
            try (value) {
                Assertions.assertEquals(body, new String(value.byteBody().buffer().join().toByteArray(), StandardCharsets.UTF_8));
            }
        }
    }

    private static final class TestHandle extends ConnectionManager.PoolHandle {
        final Runnable onPipelineBuilt;
        boolean tainted;
        int releases;

        TestHandle(EmbeddedChannel channel, Http1ResponseHandler responseHandler, Runnable onPipelineBuilt) {
            super(false, channel, responseHandler);
            this.onPipelineBuilt = onPipelineBuilt;
        }

        @Override
        public void taint() {
            tainted = true;
        }

        @Override
        public void release() {
            super.release();
            releases++;
        }

        @Override
        public boolean canReturn() {
            return !tainted;
        }

        @Override
        public void notifyRequestPipelineBuilt() {
            onPipelineBuilt.run();
        }
    }

    /**
     * A body publisher whose items are emitted by the test.
     */
    private static final class TestPublisher implements Publisher<ReadBuffer> {
        final AtomicBoolean subscribed = new AtomicBoolean();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<@Nullable Subscriber<? super ReadBuffer>> subscriber = new AtomicReference<>();
        final AtomicInteger requested = new AtomicInteger();

        @Override
        public void subscribe(Subscriber<? super ReadBuffer> s) {
            subscribed.set(true);
            subscriber.set(s);
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested.addAndGet((int) Math.min(n, Integer.MAX_VALUE));
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }

        void emit(String text) {
            Subscriber<? super ReadBuffer> s = subscriber.get();
            Assertions.assertNotNull(s);
            s.onNext(ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8));
        }
    }
}
