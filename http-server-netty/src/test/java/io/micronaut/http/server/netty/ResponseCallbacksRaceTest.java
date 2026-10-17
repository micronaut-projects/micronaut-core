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
package io.micronaut.http.server.netty;

import io.micronaut.http.body.ByteBody;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.netty.handler.codec.http.HttpResponse;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interleavings of a registration with the final write of the response, driven step by
 * step against a connection whose state the test controls.
 */
class ResponseCallbacksRaceTest {

    @Test
    void aRegistrationAfterAnUnrecordedFinalWriteIsRefused() {
        // the final write was issued before the registration could have it recorded
        FakeAccess access = new FakeAccess();
        access.finalWriteIssued = true;
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        assertFalse(callbacks.whenCompleted(t -> { }));
        callbacks.cleanedUp();
    }

    @Test
    void aRegistrationBeforeTheFinalWriteWaitsForIt() {
        FakeAccess access = new FakeAccess();
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        AtomicReference<Optional<Throwable>> outcome = new AtomicReference<>();
        assertTrue(callbacks.whenCompleted(t -> outcome.set(Optional.ofNullable(t))));
        assertTrue(access.record);
        // the final write is issued, recorded, and the request cleaned up before it completes
        ChannelPromise write = access.issueFinalWrite();
        callbacks.cleanedUp();
        assertNull(outcome.get());
        IOException failure = new IOException("write failed");
        write.setFailure(failure);
        assertSame(failure, outcome.get().orElseThrow());
    }

    @Test
    void anAbandonmentRegistrationAfterAnUnrecordedFinalWriteIsRefused() {
        FakeAccess access = new FakeAccess();
        access.finalWriteIssued = true;
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        assertNull(callbacks.whenAbandoned(() -> { }), "the callback could not be kept while the write is in progress");
        assertTrue(access.abandonCallbacks.isEmpty());
    }

    @Test
    void abandonmentCallbacksSeeTheCloseThatFailedTheFinalWrite() {
        FakeAccess access = new FakeAccess();
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        List<String> runs = new ArrayList<>();
        assertNotNull(callbacks.whenAbandoned(() -> runs.add("abandoned")));
        ChannelPromise write = access.issueFinalWrite();
        callbacks.cleanedUp();
        // the close fails the queued write first, then reports the close in the same turn
        write.setFailure(new IOException("closed"));
        access.abandoned = true;
        access.abandonCallbacks.forEach(Runnable::run);
        assertEquals(List.of("abandoned"), runs);
        access.channel.runPendingTasks();
        assertTrue(access.abandonCallbacks.isEmpty(), "removed in the next turn");
    }

    @Test
    void theFirstFailureOfTheRequestIsTheReportedOne() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 200; round++) {
                NettyHttpRequest<Object> request = new NettyHttpRequest<>(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                    NettyByteBodyFactory.empty(),
                    channel.pipeline().firstContext(),
                    ConversionService.SHARED,
                    new HttpServerConfiguration()
                );
                request.outboundAccess(new FakeAccess());
                AtomicReference<Throwable> reported = new AtomicReference<>();
                assertTrue(request.whenResponseCompleted(reported::set));
                IllegalStateException a = new IllegalStateException("a");
                IllegalStateException b = new IllegalStateException("b");
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<?> first = pool.submit(() -> {
                    barrier.await();
                    request.responseFailed(a);
                    return null;
                });
                Future<?> second = pool.submit(() -> {
                    barrier.await();
                    request.responseFailed(b);
                    return null;
                });
                first.get();
                second.get();
                // a later failure does not replace the first one, whichever it was
                request.responseFailed(new IllegalStateException("late"));
                request.responseCleanedUp();
                assertTrue(reported.get() == a || reported.get() == b, String.valueOf(reported.get()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void abandonmentCallbacksStayUntilTheFinalWriteCompletes() {
        FakeAccess access = new FakeAccess();
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        List<String> runs = new ArrayList<>();
        callbacks.whenAbandoned(() -> runs.add("abandoned"));
        ChannelPromise write = access.issueFinalWrite();
        callbacks.cleanedUp();
        assertEquals(1, access.abandonCallbacks.size(), "still registered while the write is in progress");
        access.abandoned = true;
        access.abandonCallbacks.forEach(Runnable::run);
        assertEquals(List.of("abandoned"), runs);
        write.setSuccess();
        assertTrue(access.abandonCallbacks.isEmpty(), "removed once the write completed");
    }

    static final class FakeAccess implements OutboundAccess {
        final List<Runnable> abandonCallbacks = new ArrayList<>();
        volatile boolean finalWriteIssued;
        volatile boolean record;
        volatile boolean abandoned;
        final EmbeddedChannel channel = new EmbeddedChannel();
        @Nullable ChannelPromise finalWrite;

        ChannelPromise issueFinalWrite() {
            finalWriteIssued = true;
            ChannelPromise promise = new DefaultChannelPromise(channel);
            if (record) {
                finalWrite = promise;
            }
            return promise;
        }

        @Override
        public boolean isAbandoned() {
            return abandoned;
        }

        @Override
        public boolean recordFinalWrite() {
            record = true;
            return !finalWriteIssued;
        }

        @Override
        public @Nullable ChannelFuture finalWrite() {
            return finalWrite;
        }

        @Override
        public void attachment(Object attachment) {
            // not used by the callbacks
        }

        @Override
        public void closeAfterWrite() {
            // not used by the callbacks
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
            // not used by the callbacks
        }

        @Override
        public void abort() {
            // not used by the callbacks
        }

        @Override
        public Runnable whenAbandoned(Runnable callback) {
            abandonCallbacks.add(callback);
            return () -> abandonCallbacks.remove(callback);
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            // not used by the callbacks
        }
    }
}
