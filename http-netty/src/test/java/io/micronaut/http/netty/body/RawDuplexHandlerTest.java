package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A write to the peer of a switched connection that fails ends the relay: the connection is
 * closed and the source of the sent body is cancelled, so that the other side of a relay does
 * not stay open.
 */
class RawDuplexHandlerTest {

    @Test
    void failedWriteCancelsTheSource() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(new IOException("The peer is gone"));
            }
        });
        RawDuplexHandler handler = new RawDuplexHandler(channel, () -> closed.set(true));
        channel.pipeline().addLast(RawDuplexHandler.NAME, handler);

        Sinks.Many<ReadBuffer> source = Sinks.many().unicast().onBackpressureBuffer();
        handler.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
            .adapt(source.asFlux().doOnCancel(() -> cancelled.set(true))));
        channel.runPendingTasks();
        source.tryEmitNext(ReadBufferFactory.getJdkFactory().copyOf("hello", StandardCharsets.UTF_8));
        channel.runPendingTasks();

        Assertions.assertTrue(cancelled.get(), "The source of the sent body was not cancelled");
        Assertions.assertFalse(channel.isOpen(), "The connection was not closed");
        Assertions.assertTrue(closed.get());
        channel.finishAndReleaseAll();
    }
}
