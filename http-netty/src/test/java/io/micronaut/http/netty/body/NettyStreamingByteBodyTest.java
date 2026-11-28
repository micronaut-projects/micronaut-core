package io.micronaut.http.netty.body;

import io.micronaut.http.body.CloseableByteBody;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@link NettyByteBodyFactory#streaming}: a writer larger than several chunks is streamed through
 * the Netty adapter, which moves each buffer onto the event loop.
 */
@Timeout(20)
class NettyStreamingByteBodyTest {
    @Test
    void aWriterLargerThanSeveralChunksIsStreamed() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ExecutorService executor = Executors.newCachedThreadPool();
        byte[] expected = new byte[1024 * 1024 + 123];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31);
        }
        try {
            NettyByteBodyFactory factory = new NettyByteBodyFactory(ByteBufAllocator.DEFAULT, group.next());
            try (CloseableByteBody body = factory.streaming(executor, out -> {
                // small, overflowing and multi chunk writes
                int off = 0;
                out.write(expected[off++]);
                for (int i = 0; i < 100; i++) {
                    out.write(expected, off, 500);
                    off += 500;
                }
                out.write(expected, off, expected.length - off);
            }); InputStream in = body.toInputStream()) {
                Assertions.assertArrayEquals(expected, in.readAllBytes());
            }
        } finally {
            executor.shutdownNow();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }
}
