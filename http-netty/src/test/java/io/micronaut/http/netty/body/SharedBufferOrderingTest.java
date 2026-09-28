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
package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The order of the inputs and the subscribes of a {@link StreamingNettyByteBody.SharedBuffer}
 * that are queued on its event loop.
 */
@Timeout(10)
class SharedBufferOrderingTest {

    /**
     * Each delivery asks for more, and the source hands over its next buffer right away: an input
     * triggered by the last queued input is queued behind it too, instead of entering the buffer
     * while it delivers.
     */
    @Test
    void anInputTriggeredByTheLastQueuedInputIsQueued() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        Deque<String> source = new ArrayDeque<>(List.of("b", "c"));
        List<Throwable> failures = new ArrayList<>();
        StreamingNettyByteBody.SharedBuffer[] buffer = new StreamingNettyByteBody.SharedBuffer[1];
        BufferConsumer.Upstream upstream = bytesConsumed -> {
            String next = source.poll();
            if (next != null) {
                try {
                    buffer[0].add(factory.readBufferFactory().copyOf(next, StandardCharsets.UTF_8));
                } catch (Throwable t) {
                    failures.add(t);
                }
            }
        };
        buffer[0] = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, upstream);
        StreamingNettyByteBody root = new StreamingNettyByteBody(buffer[0]);
        buffer[0].add(factory.readBufferFactory().copyOf("a", StandardCharsets.UTF_8));

        List<String> received = new ArrayList<>();
        root.primary(new BufferConsumer() {
            @Override
            public void add(ReadBuffer rb) {
                try (rb) {
                    received.add(rb.toString(StandardCharsets.UTF_8));
                }
                upstream.onBytesConsumed(1);
            }

            @Override
            public void complete() {
            }

            @Override
            public void error(Throwable e) {
                failures.add(e);
            }
        });
        channel.runPendingTasks();

        Assertions.assertEquals(List.of(), failures);
        Assertions.assertEquals(List.of("a", "b", "c"), received);
        channel.finishAndReleaseAll();
    }

    /**
     * A split off the event loop counts its reservation before it queues it. A body closed on the
     * event loop in between queues its subscribe first: that subscribe must not take the last
     * reservation before the split has reserved its own.
     */
    @Test
    void aSubscribeQueuedBeforeACountedReservationWaitsForIt() throws Exception {
        CountDownLatch reservationCounted = new CountDownLatch(1);
        CountDownLatch subscribeQueued = new CountDownLatch(1);
        Thread reserver = Thread.currentThread();
        AtomicBoolean gate = new AtomicBoolean();
        try (GatedEventLoop loop = new GatedEventLoop(() -> {
            if (reserver.equals(Thread.currentThread()) && gate.compareAndSet(true, false)) {
                reservationCounted.countDown();
                try {
                    Assertions.assertTrue(subscribeQueued.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        })) {
            LocalChannel loopChannel = new LocalChannel();
            loop.register(loopChannel).sync();
            NettyByteBodyFactory factory = new NettyByteBodyFactory(loopChannel);
            CloseableByteBody root = factory.adaptNetty(Flux.just("abc", "def")
                .map(s -> (ByteBuf) Unpooled.copiedBuffer(s, StandardCharsets.UTF_8)));

            loop.execute(() -> {
                try {
                    Assertions.assertTrue(reservationCounted.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                root.close();
                subscribeQueued.countDown();
            });
            gate.set(true);
            CloseableByteBody split = root.split(ByteBody.SplitBackpressureMode.FASTEST);

            String text = Flux.from(split.toReadBufferPublisher())
                .map(b -> {
                    try (b) {
                        return b.toString(StandardCharsets.UTF_8);
                    }
                })
                .reduce("", String::concat)
                .block(Duration.ofSeconds(5));
            Assertions.assertEquals("abcdef", text);
            loopChannel.close().sync();
        }
    }

    /**
     * An event loop that runs a hook before it queues a task.
     */
    private static final class GatedEventLoop extends SingleThreadIoEventLoop {
        private final Runnable beforeExecute;

        GatedEventLoop(Runnable beforeExecute) {
            super(null, Executors.defaultThreadFactory(), LocalIoHandler.newFactory());
            this.beforeExecute = beforeExecute;
        }

        @Override
        public void execute(Runnable task) {
            beforeExecute.run();
            super.execute(task);
        }
    }
}
