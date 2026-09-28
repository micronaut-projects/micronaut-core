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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReplayableByteBody;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Buffer ownership of {@code ByteBodyFactory.limit} and {@code ByteBodyFactory.replayable} with
 * reference counted Netty buffers: every buffer is released on success, on a failure after the
 * limit, on cancel and when a replay is refused after the limit.
 */
@Timeout(10)
class ByteBodyLimitReplayOwnershipTest {
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
    private final List<ByteBuf> allocated = new ArrayList<>();

    @AfterEach
    void allReleased() {
        channel.finishAndReleaseAll();
        for (ByteBuf buf : allocated) {
            Assertions.assertEquals(0, buf.refCnt(), "a buffer was not released");
        }
    }

    private ByteBuf buf(String text) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
        buf.writeCharSequence(text, StandardCharsets.UTF_8);
        allocated.add(buf);
        return buf;
    }

    private CloseableByteBody streamed(String... parts) {
        return factory.adaptNetty(Flux.fromArray(parts).map(this::buf));
    }

    private static String read(CloseableByteBody body) {
        return Flux.from(body.toReadBufferPublisher())
            .map(b -> {
                try (b) {
                    return b.toString(StandardCharsets.UTF_8);
                }
            })
            .reduce("", String::concat)
            .block(Duration.ofSeconds(5));
    }

    private static String buffer(CloseableByteBody body) throws Exception {
        try (CloseableAvailableByteBody available = body.buffer().get(5, TimeUnit.SECONDS)) {
            return available.toString(StandardCharsets.UTF_8);
        }
    }

    private static void cancelAfterFirst(CloseableByteBody body) {
        List<ReadBuffer> received = new ArrayList<>();
        Flux.from(body.toReadBufferPublisher()).subscribe(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(1);
            }

            @Override
            protected void hookOnNext(ReadBuffer value) {
                value.close();
                received.add(value);
                cancel();
            }
        });
        Assertions.assertEquals(1, received.size());
    }

    @Test
    void limitWithinTheLimit() throws Exception {
        Assertions.assertEquals("abcdef", buffer(factory.limit(streamed("abc", "def"), 6)));
    }

    @Test
    void limitStreamedWithinTheLimit() {
        Assertions.assertEquals("abcdef", read(factory.limit(streamed("abc", "def"), 6)));
    }

    @Test
    void limitFailsAfterTheLimit() {
        CloseableByteBody limited = factory.limit(streamed("abc", "def", "ghi"), 4);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> buffer(limited));
        Assertions.assertInstanceOf(ContentLengthExceededException.class, e.getCause());
    }

    @Test
    void limitStreamedFailsAfterTheLimit() {
        CloseableByteBody limited = factory.limit(streamed("abc", "def", "ghi"), 4);
        Assertions.assertThrows(ContentLengthExceededException.class, () -> read(limited));
    }

    @Test
    void limitCancel() {
        cancelAfterFirst(factory.limit(streamed("abc", "def", "ghi"), 100));
    }

    @Test
    void limitClosedUnread() {
        factory.limit(streamed("abc", "def"), 100).close();
    }

    @Test
    void replayableSuccess() {
        try (ReplayableByteBody replayable = factory.replayable(streamed("abc", "def"), 100)) {
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertEquals("abcdef", read(replayable.next()));
        }
    }

    @Test
    void replayableAvailableBody() {
        try (ReplayableByteBody replayable = factory.replayable(factory.adapt(buf("abcdef")), 1)) {
            Assertions.assertEquals("abcdef", read(replayable.next()));
            Assertions.assertEquals("abcdef", read(replayable.next()));
        }
    }

    @Test
    void replayableAfterTheLimit() {
        try (ReplayableByteBody replayable = factory.replayable(streamed("abc", "def", "ghi"), 4)) {
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
            Assertions.assertFalse(replayable.isReplayable());
            Assertions.assertThrows(BufferLengthExceededException.class, replayable::next);
        }
    }

    @Test
    void replayableLimitPassedWhileASecondReaderIsUnsubscribed() {
        Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer();
        try (ReplayableByteBody replayable = factory.replayable(factory.adaptNetty(sink.asFlux()), 4)) {
            CloseableByteBody first = replayable.next();
            CloseableByteBody second = replayable.next();
            sink.tryEmitNext(buf("abc")).orThrow();
            sink.tryEmitNext(buf("def")).orThrow();
            sink.tryEmitComplete().orThrow();
            Assertions.assertEquals("abcdef", read(first));
            // the bytes kept for a reader that has not subscribed yet count against the limit
            Assertions.assertThrows(BufferLengthExceededException.class, () -> read(second));
            Assertions.assertThrows(BufferLengthExceededException.class, replayable::next);
        }
    }

    @Test
    void replayableReaderCancels() {
        try (ReplayableByteBody replayable = factory.replayable(streamed("abc", "def", "ghi"), 100)) {
            cancelAfterFirst(replayable.next());
            Assertions.assertEquals("abcdefghi", read(replayable.next()));
        }
    }

    @Test
    void replayableClosedWithAnUnreadNext() {
        ReplayableByteBody replayable = factory.replayable(streamed("abc", "def"), 100);
        CloseableByteBody next = replayable.next();
        replayable.close();
        next.close();
    }

    @Test
    void replayableClosedUnread() {
        factory.replayable(streamed("abc", "def"), 100).close();
    }
}
