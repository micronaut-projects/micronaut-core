package io.micronaut.http.netty.body;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
