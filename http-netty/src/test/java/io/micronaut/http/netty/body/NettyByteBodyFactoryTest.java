package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class NettyByteBodyFactoryTest {
    @Test
    void availableBodiesCanBeConvertedOffTheEventLoop() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ByteBuf bytes = Unpooled.copiedBuffer("payload", StandardCharsets.UTF_8);
        try {
            var loop = group.next();
            assertFalse(loop.inEventLoop());
            NettyByteBodyFactory factory = new NettyByteBodyFactory(ByteBufAllocator.DEFAULT, loop);
            try (var body = factory.adapt(bytes); var stream = factory.toStreaming(body);
                 var buffered = stream.buffer().get(5, TimeUnit.SECONDS)) {
                assertEquals("payload", buffered.toString(StandardCharsets.UTF_8));
            }
            assertEquals(0, bytes.refCnt());
        } finally {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * The buffer factories hold nothing but their allocator, so those of the default allocator,
     * which nearly every channel has, are shared: a body factory is created for each response.
     */
    @Test
    void bufferFactoriesOfTheDefaultAllocatorAreShared() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            assertSame(ByteBufAllocator.DEFAULT, channel.alloc());
            NettyByteBodyFactory first = new NettyByteBodyFactory(channel);
            NettyByteBodyFactory second = new NettyByteBodyFactory(channel);
            assertSame(first.readBufferFactory(), second.readBufferFactory());
            assertSame(first.byteBufferFactory(), second.byteBufferFactory());
            assertSame(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT), first.readBufferFactory());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void anotherAllocatorGetsItsOwnBufferFactories() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            channel.config().setAllocator(UnpooledByteBufAllocator.DEFAULT);
            NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
            assertNotSame(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT), factory.readBufferFactory());
            assertSame(UnpooledByteBufAllocator.DEFAULT, ((NettyByteBufferFactory) factory.byteBufferFactory()).getNativeAllocator());
            assertNotSame(NettyByteBufferFactory.DEFAULT, factory.byteBufferFactory());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
