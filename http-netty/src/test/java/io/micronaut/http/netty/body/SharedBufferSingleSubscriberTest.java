package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chunk of a streaming body is handed to the only subscriber as is when nothing else reads it,
 * and as a duplicate when another reader keeps it.
 */
class SharedBufferSingleSubscriberTest {
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);

    @AfterEach
    void cleanup() {
        channel.finishAndReleaseAll();
    }

    @Test
    void theOnlySubscriberTakesTheChunkOver() {
        ByteBodyFactory.StreamingBody streamingBody = factory.createStreamingBody(BodySizeLimits.UNLIMITED, bytesConsumed -> {
        });
        Collector collector = new Collector();
        streamingBody.rootBody().toReadBufferPublisher().subscribe(collector);

        ByteBuf chunk = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8);
        streamingBody.sharedBuffer().add(((NettyReadBufferFactory) factory.readBufferFactory()).adapt(chunk));
        ByteBuf last = Unpooled.copiedBuffer("bar", StandardCharsets.UTF_8);
        streamingBody.sharedBuffer().addAndComplete(((NettyReadBufferFactory) factory.readBufferFactory()).adapt(last));

        assertEquals(2, collector.buffers.size());
        ByteBuf delivered = NettyReadBufferFactory.toByteBuf(collector.buffers.get(0));
        ByteBuf deliveredLast = NettyReadBufferFactory.toByteBuf(collector.buffers.get(1));
        try {
            assertSame(chunk, delivered);
            assertSame(last, deliveredLast);
            assertEquals(1, chunk.refCnt());
            assertEquals("foo", delivered.toString(StandardCharsets.UTF_8));
            assertTrue(collector.complete);
        } finally {
            delivered.release();
            deliveredLast.release();
        }
        assertEquals(0, chunk.refCnt());
        assertEquals(0, last.refCnt());
    }

    @Test
    void aSecondReaderKeepsItsCopy() throws Exception {
        ByteBodyFactory.StreamingBody streamingBody = factory.createStreamingBody(BodySizeLimits.UNLIMITED, bytesConsumed -> {
        });
        // a split reserves a reader, which needs the bytes buffered for it
        var split = streamingBody.rootBody().split();
        Collector collector = new Collector();
        streamingBody.rootBody().toReadBufferPublisher().subscribe(collector);

        ByteBuf chunk = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8);
        streamingBody.sharedBuffer().add(((NettyReadBufferFactory) factory.readBufferFactory()).adapt(chunk));
        streamingBody.sharedBuffer().complete();

        ByteBuf delivered = NettyReadBufferFactory.toByteBuf(collector.buffers.get(0));
        try {
            assertNotSame(chunk, delivered);
            assertEquals("foo", delivered.toString(StandardCharsets.UTF_8));
        } finally {
            delivered.release();
        }
        try (split; var available = split.buffer().get()) {
            assertEquals("foo", new String(available.toByteArray(), StandardCharsets.UTF_8));
        }
        assertEquals(0, chunk.refCnt());
    }

    private static final class Collector implements Subscriber<ReadBuffer> {
        final List<ReadBuffer> buffers = new ArrayList<>();
        boolean complete;

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ReadBuffer readBuffer) {
            buffers.add(readBuffer);
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
