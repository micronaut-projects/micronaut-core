package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A {@link FormDemuxer} that fails must report the failure through its field publisher and
 * release every buffer it holds, instead of throwing out of its constructor.
 */
class FormDemuxerReleaseTest {
    private EmbeddedChannel channel;
    private TrackingAllocator alloc;
    private NettyByteBodyFactory byteBodyFactory;

    @BeforeEach
    void setup() {
        alloc = new TrackingAllocator();
        channel = new EmbeddedChannel();
        channel.config().setAllocator(alloc);
        byteBodyFactory = new NettyByteBodyFactory(channel);
    }

    private ByteBuf tracked(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        return alloc.buffer(bytes.length).writeBytes(bytes);
    }

    /**
     * a field without '=' over the form-max-fields limit is only seen at the end
     * of the input, and the TooManyFormFieldsException escapes complete().
     */
    @Test
    void tooManyFieldsAtEndOfInputIsReportedAndReleased() {
        AtomicReference<FormDemuxer> demuxer = new AtomicReference<>();
        assertDoesNotThrow(() -> demuxer.set(new FormDemuxer(
            PostBodyDecoder.builder().maxFields(2).forUrlEncodedData(), null, StandardCharsets.UTF_8, channel,
            BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED,
            byteBodyFactory.adapt(tracked("a=" + "1".repeat(4096) + "&a=" + "1".repeat(4096) + "&x")))));

        Collected collected = collect(demuxer.get());
        assertNotNull(collected.error, "the field limit must fail the form");
        assertEquals(0, alloc.retainedBytes(), "buffers of a form refused for too many fields are released");
    }

    /**
     * the part that is still open when a streamed body is handed to the
     * demuxer has a malformed header; devolveIfStillBuffering() throws out of the constructor and
     * the complete field queued before it is never released.
     */
    @Test
    void malformedOpenPartHeaderInConstructorIsReportedAndReleased() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        String boundary = "b0undary";
        streamingBody.sharedBuffer().add(byteBodyFactory.readBufferFactory().adapt(tracked(
            "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"padding\"\r\n\r\n"
                + "Z".repeat(64 * 1024) + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"; filename*=UTF-8''%ZZ\r\n\r\n"
                + "start of the content")));

        AtomicReference<FormDemuxer> demuxer = new AtomicReference<>();
        assertDoesNotThrow(() -> demuxer.set(new FormDemuxer(
            PostBodyDecoder.builder().forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel,
            BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody())));

        Collected collected = collect(demuxer.get());
        // the client sends more of the body after the failure
        streamingBody.sharedBuffer().add(byteBodyFactory.readBufferFactory().adapt(tracked("Y".repeat(64 * 1024))));
        streamingBody.sharedBuffer().complete();
        channel.runPendingTasks();

        assertNotNull(collected.error, "a malformed part header must fail the form");
        assertEquals(0, alloc.retainedBytes(), "buffers of a form with a malformed part header are released");
    }

    /**
     * Control for the field limit: the extra field ends with '=', so the limit is hit while input
     * is added.
     */
    @Test
    void controlTooManyFieldsWhileAdding() {
        FormDemuxer demuxer = new FormDemuxer(
            PostBodyDecoder.builder().maxFields(2).forUrlEncodedData(), null, StandardCharsets.UTF_8, channel,
            BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED,
            byteBodyFactory.adapt(tracked("a=" + "1".repeat(4096) + "&a=" + "1".repeat(4096) + "&x=")));
        Collected collected = collect(demuxer);
        assertNotNull(collected.error);
        assertEquals(0, alloc.retainedBytes());
    }

    private Collected collect(FormDemuxer demuxer) {
        Collected c = new Collected();
        demuxer.fields().subscribe(c);
        channel.runPendingTasks();
        return c;
    }

    private static final class Collected implements Subscriber<RawFormField> {
        final List<RawFormField> fields = new ArrayList<>();
        Throwable error;
        boolean complete;

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(RawFormField f) {
            fields.add(f);
            f.close();
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }

    private static final class MockUpstream implements BufferConsumer.Upstream {
        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // the test adds all input up front, demand is not needed
        }
    }
}
