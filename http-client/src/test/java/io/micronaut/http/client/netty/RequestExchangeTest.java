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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class RequestExchangeTest {
    @Test
    void finalResponseBeforeContinueReleasesTheUnsentBody() {
        try (var f = new Fixture()) {
            ByteBuf bytes = Unpooled.buffer().writeByte(1);
            var exchange = f.exchange(f.factory.adapt(bytes), true);
            exchange.send();
            assertInstanceOf(io.netty.handler.codec.http.HttpRequest.class, f.channel.readOutbound());
            f.channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED));
            assertEquals(417, f.response.join().code());
            assertEquals(0, bytes.refCnt());
            assertEquals(0, f.connection.liveRequestCount());
            assertTrue(f.channel.isActive());
            assertNull(f.channel.readOutbound());
            assertEquals(1, f.completions.get());
            f.response.join().close();
        }
    }

    @Test
    void repeatedContinueWritesTheBodyOnce() {
        try (var f = new Fixture()) {
            ByteBuf bytes = Unpooled.buffer().writeByte(1);
            var exchange = f.exchange(f.factory.adapt(bytes), true);
            exchange.send();
            assertNotNull(f.channel.readOutbound());
            f.channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            HttpContent content = f.channel.readOutbound();
            assertEquals(1, content.content().readableBytes());
            content.release();
            f.channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            assertNull(f.channel.readOutbound());
            f.channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            assertEquals(0, bytes.refCnt());
            assertEquals(1, f.completions.get());
            f.response.join().close();
        }
    }

    @Test
    void requestBodyFailureAfterDeliveryKeepsTheResponseAndLeaseUntilFinish() {
        try (var f = new Fixture()) {
            Sinks.Many<ByteBuf> source = Sinks.many().unicast().onBackpressureBuffer();
            var exchange = f.exchange(f.factory.adaptNetty(source.asFlux()), false);
            exchange.send();
            f.channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            var delivered = f.response.join();
            assertEquals(1, f.connection.liveRequestCount());
            var body = delivered.byteBody().buffer().toCompletableFuture();
            source.tryEmitError(new IllegalStateException("request body failed"));
            f.channel.runPendingTasks();
            assertFalse(body.isDone());
            assertSame(delivered, f.response.join());
            assertEquals(1, f.completions.get());
            assertTrue(f.channel.isOpen());
            assertEquals(1, f.connection.liveRequestCount());
            f.channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
            body.join().close();
            assertFalse(f.channel.isOpen());
            assertEquals(0, f.connection.liveRequestCount());
            delivered.close();
        }
    }

    @Test
    void requestBodyFailureBeforeDeliveryTerminatesOnce() {
        try (var f = new Fixture()) {
            var failure = new IllegalStateException("request body failed");
            var exchange = f.exchange(f.factory.adaptNetty(Flux.error(failure)), false);
            exchange.send();
            assertTrue(f.response.isCompletedExceptionally());
            assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class, f.response::join).getCause());
            exchange.accept(new IllegalStateException("second failure"));
            assertEquals(1, f.completions.get());
            assertFalse(f.channel.isOpen());
            assertEquals(0, f.connection.liveRequestCount());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void cancellationBeforeWritingReturnsTheUnusedConnection(boolean installed, boolean streaming) {
        try (var f = new Fixture()) {
            ByteBuf bytes = Unpooled.buffer().writeByte(1);
            var exchange = f.exchange(streaming ? f.factory.adaptNetty(Flux.just(bytes).doOnDiscard(ByteBuf.class, ByteBuf::release)) : f.factory.adapt(bytes), false);
            if (installed) {
                f.pipelineBuilt = f.sink::cancel;
            } else {
                f.sink.cancel();
            }
            exchange.send();
            assertEquals(0, bytes.refCnt());
            assertTrue(f.channel.isActive());
            assertEquals(0, f.connection.liveRequestCount());
            assertNull(f.channel.pipeline().context(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE));
            assertNull(f.channel.readOutbound());
            var next = f.holder.pool.createPendingRequest(null);
            next.dispatch();
            var nextHandle = next.flow().toCompletableFuture().join();
            assertSame(f.channel, nextHandle.channel());
            nextHandle.release();
        }
    }

    @Test
    void cancellationDuringRequestStreamingStopsTheSource() {
        try (var f = new Fixture()) {
            var cancelled = new AtomicBoolean();
            var exchange = f.exchange(f.factory.adaptNetty(Flux.<ByteBuf>never().doOnCancel(() -> cancelled.set(true))), false);
            exchange.send();
            assertEquals(1, f.connection.liveRequestCount());
            f.sink.cancel();
            f.channel.runPendingTasks();
            assertTrue(cancelled.get());
            assertFalse(f.channel.isOpen());
            assertEquals(0, f.connection.liveRequestCount());
        }
    }

    @Test
    void deliveredResponseKeepsTheLeaseUntilItsStreamingBodyEnds() {
        try (var f = new Fixture()) {
            var exchange = f.exchange(NettyByteBodyFactory.empty(), false);
            exchange.send();
            f.channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            var delivered = f.response.join();
            f.sink.cancel();
            assertTrue(f.channel.isActive());
            assertEquals(1, f.connection.liveRequestCount());
            delivered.close();
            f.channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
            assertEquals(0, f.connection.liveRequestCount());
            assertTrue(f.channel.isActive());
            assertEquals(1, f.completions.get());
        }
    }

    @Test
    void cancellationVisibleBeforeItsLoopTaskDiscardsAnArrivingResponse() throws Exception {
        try (var f = new Fixture()) {
            var exchange = f.exchange(NettyByteBodyFactory.empty(), false);
            exchange.send();
            Thread cancel = new Thread(f.sink::cancel);
            cancel.start();
            cancel.join();
            ByteBuf bytes = Unpooled.buffer().writeByte(1);
            exchange.complete(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), f.factory.adapt(bytes));
            f.channel.runPendingTasks();
            assertEquals(0, bytes.refCnt());
            assertTrue(f.channel.isActive());
            assertEquals(1, f.connection.liveRequestCount());
            assertEquals(0, f.completions.get());
            exchange.finish(f.channel.pipeline().firstContext());
            assertEquals(0, f.connection.liveRequestCount());
            assertTrue(f.channel.isActive());
        }
    }

    @Test
    void terminationIsSingleAndCannotReleaseTheNextLease() {
        try (var f = new Fixture()) {
            var exchange = f.exchange(NettyByteBodyFactory.empty(), false);
            exchange.send();
            f.channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            f.response.join().close();
            var next = f.holder.pool.createPendingRequest(null);
            f.connection.dispatch0(next);
            var nextHandle = next.flow().toCompletableFuture().join();
            assertEquals(1, f.connection.liveRequestCount());
            exchange.finish(f.channel.pipeline().firstContext());
            exchange.accept(new IllegalStateException("late body error"));
            exchange.run();
            assertEquals(1, f.connection.liveRequestCount());
            assertTrue(f.channel.isActive());
            assertEquals(1, f.completions.get());
            nextHandle.release();
            assertEquals(0, f.connection.liveRequestCount());
        }
    }

    @Test
    void offLoopBodyFailureTaintsBeforeAnAlreadyQueuedFinish() throws Exception {
        try (var server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             var client = DefaultHttpClient.builder().uri(URI.create("http://127.0.0.1:" + server.getLocalPort())).build()) {
            var key = new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://127.0.0.1:" + server.getLocalPort()));
            var handle = client.connectionManager().connect(key, null).toCompletableFuture().get(5, TimeUnit.SECONDS);
            server.setSoTimeout(5000);
            try (Socket socket = server.accept()) {
                var sink = DelayedExecutionFlow.<NettyClientByteBodyResponse>create();
                var response = sink.toCompletableFuture();
                var exchange = handle.channel().eventLoop().submit(() -> {
                    var created = client.getNettyHttpClient().new RequestExchange(handle, HttpRequest.POST("/", ""),
                        NettyByteBodyFactory.empty(), sink, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"));
                    created.complete(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty());
                    return created;
                }).get(5, TimeUnit.SECONDS);
                var failed = new CountDownLatch(1);
                var finish = handle.channel().eventLoop().submit(() -> {
                    assertTrue(failed.await(5, TimeUnit.SECONDS));
                    exchange.finish(handle.channel().pipeline().lastContext());
                    return null;
                });
                try {
                    exchange.accept(new IllegalStateException("off-loop source failure"));
                } finally {
                    failed.countDown();
                }
                finish.sync();
                handle.channel().eventLoop().submit(() -> assertFalse(handle.channel().isActive())).sync();
                response.join().close();
            } finally {
                handle.release();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DefaultHttpClient client = DefaultHttpClient.builder().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        final ConnectionManager.PoolHolder holder = client.connectionManager().createPool(
            new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://localhost")), List.of(channel.eventLoop()));
        Runnable pipelineBuilt = () -> { };
        final ConnectionManager.PoolHolder.Http1ConnectionHolder connection = holder.new Http1ConnectionHolder(channel, new NettyClientCustomizer() {
            @Override
            public void onRequestPipelineBuilt() {
                pipelineBuilt.run();
            }
        });
        final DelayedExecutionFlow<NettyClientByteBodyResponse> sink = DelayedExecutionFlow.create();
        final AtomicInteger completions = new AtomicInteger();
        final CompletableFuture<NettyClientByteBodyResponse> response = sink.toCompletableFuture()
            .whenComplete((v, e) -> completions.incrementAndGet());
        final ConnectionManager.PoolHandle handle;

        Fixture() {
            connection.release();
            var pending = holder.pool.createPendingRequest(null);
            connection.dispatch0(pending);
            handle = pending.flow().toCompletableFuture().join();
        }

        NettyHttpClient.RequestExchange exchange(CloseableByteBody body, boolean expectContinue) {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            if (expectContinue) {
                request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
            }
            return client.getNettyHttpClient().new RequestExchange(handle, HttpRequest.POST("/", ""), body, sink, request);
        }

        @Override
        public void close() {
            if (response.isDone() && !response.isCompletedExceptionally()) {
                response.join().close();
            }
            handle.release();
            channel.finishAndReleaseAll();
            holder.pool.close();
            client.close();
        }
    }
}
