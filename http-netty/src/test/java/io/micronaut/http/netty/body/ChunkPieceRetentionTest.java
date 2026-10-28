package io.micronaut.http.netty.body;

import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANT-2026-NDS9POIN: a body that arrives in one-byte pieces must not be held as one buffer object
 * per piece, or a client chooses how much heap a limit-conforming body costs.
 */
class ChunkPieceRetentionTest {
    private static final int PIECES = 20_000;
    private static final int MAX_RETAINED_PIECES = 1_000;

    @Test
    void bufferedBodyDoesNotKeepEveryPiece() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory bbf = new NettyByteBodyFactory(channel);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
        List<CloseableAvailableByteBody> result = new ArrayList<>();
        streamingBody.rootBody().bufferFlow().onComplete((b, e) -> {
            if (b != null) {
                result.add(b);
            }
        });
        List<ByteBuf> pieces = new ArrayList<>(PIECES);
        for (int i = 0; i < PIECES; i++) {
            ByteBuf piece = Unpooled.buffer(1).writeByte('A');
            pieces.add(piece);
            streamingBody.sharedBuffer().add(bbf.readBufferFactory().adapt(piece));
        }
        long retained = pieces.stream().filter(b -> b.refCnt() > 0).count();
        streamingBody.sharedBuffer().complete();
        assertTrue(retained <= MAX_RETAINED_PIECES, "a body being buffered from " + PIECES + " one-byte pieces holds " + retained + " piece buffers");
        assertEquals(1, result.size());
        try (CloseableAvailableByteBody body = result.get(0)) {
            assertEquals("A".repeat(PIECES), new String(body.toByteArray()));
        }
    }

    private static final class MockUpstream implements BufferConsumer.Upstream {
        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // the test feeds the pieces directly, there is no upstream to resume
        }
    }
}
