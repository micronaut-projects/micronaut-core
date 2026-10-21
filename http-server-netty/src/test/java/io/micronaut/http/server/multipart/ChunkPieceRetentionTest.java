package io.micronaut.http.server.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANT-2026-NDS9POIN: a body that arrives in one-byte pieces must not be held as one buffer object
 * per piece, or a client chooses how much heap a limit-conforming upload or body costs.
 */
class ChunkPieceRetentionTest {
    private static final int PIECES = 20_000;
    private static final int MAX_RETAINED_PIECES = 1_000;

    private static List<ByteBuf> feed(ByteBodyFactory.StreamingBody streamingBody, ByteBodyFactory factory) {
        List<ByteBuf> pieces = new ArrayList<>(PIECES);
        for (int i = 0; i < PIECES; i++) {
            ByteBuf piece = Unpooled.buffer(1).writeByte('A');
            pieces.add(piece);
            streamingBody.sharedBuffer().add(((NettyByteBodyFactory) factory).readBufferFactory().adapt(piece));
        }
        return pieces;
    }

    private static long retained(List<ByteBuf> pieces) {
        return pieces.stream().filter(b -> b.refCnt() > 0).count();
    }

    @Test
    void openFileUploadDoesNotKeepEveryPiece() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyHttpRequest<?> request = new NettyHttpRequest<>(
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
            AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().createEmpty()),
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter()).firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        );
        try (ApplicationContext ctx = ApplicationContext.run(Map.of())) {
            FormFactory factory = ctx.getBean(FormFactory.class);
            ByteBodyFactory bbf = request.byteBodyFactory();
            ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
            factory.completeFileUpload(request, new RawFormField(
                new FormFieldMetadata("file", "a.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE),
                streamingBody.rootBody()));
            // 20 000 bytes, far below max-file-size, and the part is still open
            List<ByteBuf> pieces = feed(streamingBody, bbf);
            long retained = retained(pieces);
            assertTrue(retained <= MAX_RETAINED_PIECES, "an open in-memory upload of " + PIECES + " one-byte pieces holds " + retained + " piece buffers");
            streamingBody.sharedBuffer().complete();
        } finally {
            request.release();
        }
    }

    @Test
    void bufferedBodyDoesNotKeepEveryPiece() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory bbf = new NettyByteBodyFactory(channel);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
        InternalByteBody.bufferFlow(streamingBody.rootBody()).onComplete((b, e) -> {
            if (b != null) {
                b.close();
            }
        });
        List<ByteBuf> pieces = feed(streamingBody, bbf);
        long retained = retained(pieces);
        streamingBody.sharedBuffer().complete();
        assertTrue(retained <= MAX_RETAINED_PIECES, "a body being buffered from " + PIECES + " one-byte pieces holds " + retained + " piece buffers");
    }

    private static final class MockUpstream implements BufferConsumer.Upstream {
        @Override
        public void onBytesConsumed(long bytesConsumed) {
        }
    }
}
