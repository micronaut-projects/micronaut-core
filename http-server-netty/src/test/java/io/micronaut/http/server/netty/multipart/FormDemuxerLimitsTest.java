package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Group 11: the form limits of a {@link FormDemuxer} whose input arrives in pieces.
 */
class FormDemuxerLimitsTest {

    /**
     * Input the demuxer holds back from the decoder still counts towards the undecoded limit.
     */
    @Test
    void pendingInputCountsTowardsUndecodedLimit() {
        int limit = 16 * 1024;
        PostBodyDecoder decoder = PostBodyDecoder.builder()
            .charset(StandardCharsets.UTF_8)
            .undecodedLimit(limit)
            .forUrlEncodedData();
        Result result = run(decoder, null, new BodySizeLimits(Long.MAX_VALUE, limit), new BodySizeLimits(Long.MAX_VALUE, limit),
            "a".repeat(8192), "a".repeat(8192), "a".repeat(8192), "=x");
        assertInstanceOf(ContentLengthExceededException.class, result.error.get(), "a 24 KiB field name over a 16 KiB undecoded limit was accepted");
    }

    /**
     * A part with a Content-Length within form-max-bytes is accepted when its first content
     * arrives before the hand-over to streaming.
     */
    @Test
    void declaredLengthWithinFormLimitIsAccepted() {
        PostBodyDecoder decoder = PostBodyDecoder.builder()
            .charset(StandardCharsets.UTF_8)
            .forMultipartBoundary("boundary");
        String head = "--boundary\r\nContent-Disposition: form-data; name=\"data\"; filename=\"a.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\nContent-Length: 1000\r\n\r\n";
        Result result = run(decoder, "boundary", BodySizeLimits.UNLIMITED, new BodySizeLimits(1500, Integer.MAX_VALUE),
            head + "a".repeat(512), "a".repeat(488) + "\r\n--boundary--\r\n");
        assertNull(result.error.get());
        assertEquals(List.of(1000), result.sizes);
    }

    private static Result run(PostBodyDecoder decoder, String boundary, BodySizeLimits fieldLimits, BodySizeLimits totalLimits, String... pieces) {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(channel);
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(BodySizeLimits.UNLIMITED, new NoUpstream());
        FormDemuxer demuxer = new FormDemuxer(decoder, boundary, StandardCharsets.UTF_8, channel, fieldLimits, totalLimits, body.rootBody());
        Result result = new Result();
        demuxer.fields().subscribe(field -> {
            try (RawFormField f = field) {
                InternalByteBody.bufferFlow(f.byteBody().move()).onComplete((b, e) -> {
                    if (e != null) {
                        result.error.compareAndSet(null, e);
                    } else {
                        try (ReadBuffer rb = b.toReadBuffer()) {
                            result.sizes.add(rb.readable());
                        }
                    }
                });
            }
        }, e -> result.error.compareAndSet(null, e));
        for (String piece : pieces) {
            body.sharedBuffer().add(factory.readBufferFactory().adapt(Unpooled.copiedBuffer(piece, StandardCharsets.ISO_8859_1)));
            channel.runPendingTasks();
        }
        body.sharedBuffer().complete();
        channel.runPendingTasks();
        channel.finishAndReleaseAll();
        return result;
    }

    private static final class Result {
        final List<Integer> sizes = new ArrayList<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
    }

    private static final class NoUpstream implements BufferConsumer.Upstream {
        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // the test feeds all pieces itself
        }
    }
}
