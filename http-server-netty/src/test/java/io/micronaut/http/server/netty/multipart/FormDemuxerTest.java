package io.micronaut.http.server.netty.multipart;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.DecoderQuirk;
import io.netty.contrib.multipart.FormDecoderException;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FormDemuxerTest {
    private EmbeddedChannel channel;
    private NettyByteBodyFactory byteBodyFactory;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel();
        byteBodyFactory = new NettyByteBodyFactory(channel);
    }

    private FormDemuxer createUrlEncoded(ByteBody body) {
        return new FormDemuxer(PostBodyDecoder.builder().forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, body);
    }

    private FormDemuxer createMultipart(ByteBody body, String boundary) {
        return new FormDemuxer(PostBodyDecoder.builder().forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, body);
    }

    private static <T> Queue<T> toQueue(Publisher<T> publisher) {
        QueueSubscriber<T> subscriber = new QueueSubscriber<>();
        subscriber.noBackpressure();
        publisher.subscribe(subscriber);
        return subscriber.queue;
    }

    private void write(BaseSharedBuffer dst, String msg) {
        dst.add(byteBodyFactory.readBufferFactory().copyOf(msg, StandardCharsets.UTF_8));
    }

    private static void assertBadRequest(Throwable error) {
        HttpStatusException e = assertInstanceOf(HttpStatusException.class, error);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertInstanceOf(FormDecoderException.class, e.getCause());
    }

    private static QueueSubscriber<String> content(ByteBody body) {
        QueueSubscriber<String> subscriber = new QueueSubscriber<>();
        Flux.from(body.toByteArrayPublisher())
            .map(bb -> new String(bb, StandardCharsets.UTF_8))
            .subscribe(subscriber);
        return subscriber;
    }

    @Test
    public void simpleAvailable() {
        Queue<RawFormField> fields = toQueue(createUrlEncoded(byteBodyFactory.copyOf("foo=bar", StandardCharsets.UTF_8)).fields());

        RawFormField field = fields.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("bar", data.queue.poll());
        assertTrue(data.complete);
    }

    @Test
    public void multipartContentTypeMixedCase() {
        // Regression test: real clients (curl, browsers, MultipartBody) send the conventional
        // mixed-case "Content-Type" header on multipart parts. This must be recognized.
        String boundary = "boundary1234";
        String body = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"test.pdf\"\r\n" +
            "Content-Type: application/pdf\r\n" +
            "\r\n" +
            "file content\r\n" +
            "--" + boundary + "--\r\n";

        Queue<RawFormField> fields = toQueue(createMultipart(byteBodyFactory.copyOf(body, StandardCharsets.UTF_8), boundary).fields());

        RawFormField field = fields.remove();
        assertEquals("file", field.metadata().name());
        assertEquals("test.pdf", field.metadata().fileName());
        assertEquals(MediaType.of("application/pdf"), field.metadata().mediaType());
        content(field.byteBody()).noBackpressure();
    }

    @Test
    public void multipartContentTypeLowerCase() {
        // lower-case header names must also be recognized, since HTTP header names are
        // case-insensitive per RFC 7230 section 3.2.
        String boundary = "boundary1234";
        String body = "--" + boundary + "\r\n" +
            "content-disposition: form-data; name=\"file\"; filename=\"test.pdf\"\r\n" +
            "content-type: application/pdf\r\n" +
            "\r\n" +
            "file content\r\n" +
            "--" + boundary + "--\r\n";

        Queue<RawFormField> fields = toQueue(createMultipart(byteBodyFactory.copyOf(body, StandardCharsets.UTF_8), boundary).fields());

        RawFormField field = fields.remove();
        assertEquals("file", field.metadata().name());
        assertEquals(MediaType.of("application/pdf"), field.metadata().mediaType());
        content(field.byteBody()).noBackpressure();
    }

    @Test
    public void decodingASplitLeavesTheBytesOfTheOtherReadersIntact() {
        // a filter decodes a copy of the form as it arrives, and the route decodes the form: the
        // decoder of the copy must not write into the bytes that are buffered for the route
        String boundary = "boundary1234";
        String body = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"name\"\r\n" +
            "\r\n" +
            "Fred\r\n" +
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.txt\"\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "picture\r\n" +
            "--" + boundary + "--\r\n";
        for (int split = 1; split < body.length(); split++) {
            MockUpstream upstream = new MockUpstream();
            ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
            CloseableByteBody copy = streamingBody.rootBody().split(ByteBody.SplitBackpressureMode.FASTEST);
            StringBuilder copied = new StringBuilder();
            Flux.from(createMultipart(copy, boundary).fields()).subscribe(field -> {
                copied.append(field.metadata().name()).append(';');
                field.close();
            });

            // like the contents the HTTP codec reads: slices of what the connection received
            ByteBuf received = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
            streamingBody.sharedBuffer().add(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(received.retainedSlice(0, split)));
            streamingBody.sharedBuffer().add(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(received.retainedSlice(split, body.length() - split)));
            received.release();
            streamingBody.sharedBuffer().complete();
            assertEquals("name;avatar;", copied.toString(), "the copy, split at " + split);

            StringBuilder read = new StringBuilder();
            Flux.from(createMultipart(streamingBody.rootBody(), boundary).fields()).subscribe(field -> {
                read.append(field.metadata().name()).append(';');
                field.close();
            });
            assertEquals("name;avatar;", read.toString(), "the body, split at " + split);
        }
    }

    @Test
    public void multipartCloseDelimiterSplitAcrossWrites() {
        String boundary = "b-b";
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder().forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody())
            .fields().subscribe(fields.noBackpressure());

        // the content repeats parts of the delimiter, and every byte arrives on its own
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"a\"\r\n"
            + "\r\n"
            + "x\r\n-b-b--\r\n--b-\r\n"
            + "--" + boundary + "--\r\nepilogue";
        for (char c : body.toCharArray()) {
            write(streamingBody.sharedBuffer(), String.valueOf(c));
        }
        RawFormField field = fields.queue.remove();
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        streamingBody.sharedBuffer().complete();

        assertEquals("x\r\n-b-b--\r\n--b-", String.join("", data.queue));
        assertNull(fields.error);
        assertTrue(fields.complete);
    }

    @Test
    public void multipartWithoutCloseDelimiter() {
        String boundary = "b-b";
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder().forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody())
            .fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"a\"\r\n"
            + "\r\n"
            + "x\r\n"
            + "--" + boundary + "\r\n");
        RawFormField field = fields.queue.remove();
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        streamingBody.sharedBuffer().complete();

        assertEquals("x", String.join("", data.queue));
        HttpStatusException error = assertInstanceOf(HttpStatusException.class, fields.error);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    public void multipartLfOnlyCloseDelimiterSplitAcrossWrites() {
        String boundary = "b-b";
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        createMultipart(streamingBody.rootBody(), boundary).fields().subscribe(fields.noBackpressure());

        // the decoder accepts lines that end with a bare LF, and so must the close delimiter
        String body = "--" + boundary + "\n"
            + "Content-Disposition: form-data; name=\"a\"\n"
            + "\n"
            + "x\n-\n--b-\n"
            + "--" + boundary + "--";
        for (char c : body.toCharArray()) {
            write(streamingBody.sharedBuffer(), String.valueOf(c));
        }
        RawFormField field = fields.queue.remove();
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        streamingBody.sharedBuffer().complete();

        assertEquals("x\n-\n--b-", String.join("", data.queue));
        assertNull(fields.error);
        assertTrue(fields.complete);
    }

    @Test
    public void multipartCloseDelimiterInTheCharsetOfTheDecoder() {
        // the decoder encodes the boundary with its charset, so a boundary outside of ASCII must
        // be matched in the same encoding
        String boundary = "b\u00e9b";
        QueueSubscriber<RawFormField> subscriber = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder().charset(StandardCharsets.UTF_8).forMultipartBoundary(boundary), boundary, StandardCharsets.UTF_8, channel,
            BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED,
            byteBodyFactory.copyOf("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n"
                + "\r\n"
                + "x\r\n"
                + "--" + boundary + "--\r\n", StandardCharsets.UTF_8))
            .fields().subscribe(subscriber.noBackpressure());

        RawFormField field = subscriber.queue.remove();
        assertEquals("a", field.metadata().name());
        field.close();
        assertNull(subscriber.error);
        assertTrue(subscriber.complete);
    }

    @Test
    public void simpleStreaming() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Queue<RawFormField> fields = toQueue(createUrlEncoded(streamingBody.rootBody()).fields());

        write(streamingBody.sharedBuffer(), "foo=bar");

        RawFormField field = fields.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("bar", data.queue.poll());

        write(streamingBody.sharedBuffer(), "baz");
        assertEquals("baz", data.queue.poll());

        streamingBody.sharedBuffer().complete();
        assertNull(fields.poll());
        assertTrue(data.complete);
    }

    @Test
    public void backpressure() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Queue<RawFormField> fields = toQueue(createUrlEncoded(streamingBody.rootBody()).fields());

        write(streamingBody.sharedBuffer(), "foo");
        assertEquals(3, upstream.consumed);
        assertNull(fields.poll());

        write(streamingBody.sharedBuffer(), "=");
        assertEquals(4, upstream.consumed);
        RawFormField field = fields.remove();
        assertEquals("foo", field.metadata().name());

        QueueSubscriber<String> data = content(field.byteBody());

        write(streamingBody.sharedBuffer(), "bar");
        assertEquals(4, upstream.consumed);

        data.request(1);
        assertEquals("bar", data.queue.poll());
        assertEquals(7, upstream.consumed);
    }

    @Test
    public void cancelBody() {
        // If an individual body is cancelled, the upstream should not be. Other fields still need
        // to be processed.

        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Queue<RawFormField> fields = toQueue(createUrlEncoded(streamingBody.rootBody()).fields());

        write(streamingBody.sharedBuffer(), "foo=bar");
        var field1 = fields.remove();
        assertEquals("foo", field1.metadata().name());
        var data1 = content(field1.byteBody()).noBackpressure();
        assertEquals("bar", data1.queue.poll());

        data1.cancel();

        assertFalse(upstream.allowDiscard);
        write(streamingBody.sharedBuffer(), "baz");
        assertNull(data1.queue.poll()); // further input ignored for this field

        write(streamingBody.sharedBuffer(), "&fizz=buzz");
        streamingBody.sharedBuffer().complete();
        var field2 = fields.remove();
        assertEquals("fizz", field2.metadata().name());
        var data2 = content(field2.byteBody()).noBackpressure();
        assertEquals("buzz", data2.queue.poll());
        assertTrue(data2.complete);
    }

    @Test
    public void cancelFlux() {
        // If the field publisher is cancelled, we should wait with cancelling the upstream until
        // the current body is finished.

        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        createUrlEncoded(streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=bar");
        var field1 = fields.queue.remove();
        assertEquals("foo", field1.metadata().name());
        var data1 = content(field1.byteBody()).noBackpressure();
        assertEquals("bar", data1.queue.poll());

        fields.cancel();
        assertFalse(upstream.allowDiscard);

        write(streamingBody.sharedBuffer(), "baz");
        assertEquals("baz", data1.queue.poll());

        write(streamingBody.sharedBuffer(), "&fizz=");
        assertTrue(data1.complete);
        assertNull(fields.queue.poll());
        assertTrue(upstream.allowDiscard);
    }

    @Test
    public void cancelBoth() {
        // If the both the field publisher and the current body are cancelled, we should also
        // cancel the upstream.

        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        createUrlEncoded(streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=bar");
        var field1 = fields.queue.remove();
        assertEquals("foo", field1.metadata().name());
        var data1 = content(field1.byteBody()).noBackpressure();
        assertEquals("bar", data1.queue.poll());

        fields.cancel();
        data1.cancel();

        assertTrue(upstream.allowDiscard);
    }

    @Test
    public void upstreamError() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        createUrlEncoded(streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=bar");

        RawFormField field = fields.queue.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("bar", data.queue.poll());

        Exception testException = new Exception("test exception");
        streamingBody.sharedBuffer().error(testException);

        assertEquals(testException, data.error);
        assertEquals(testException, fields.error);
    }

    @Test
    public void parseError1() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        createUrlEncoded(streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=ba\rr");

        RawFormField field = fields.queue.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("ba", data.queue.poll());

        assertTrue(data.complete);
        assertBadRequest(fields.error);
    }

    @Test
    public void parseError2() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder()
            .enableQuirks(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE)
            .forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=ba");

        RawFormField field = fields.queue.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("ba", data.queue.poll());

        write(streamingBody.sharedBuffer(), "%rr");

        assertBadRequest(data.error);
        assertBadRequest(fields.error);
    }

    @Test
    public void fieldBufferLimit() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder()
            .enableQuirks(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE)
            .forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, new BodySizeLimits(Long.MAX_VALUE, 6), BodySizeLimits.UNLIMITED, streamingBody.rootBody())
            .fields().subscribe(fields.noBackpressure());

        // note: the 'ba' that arrives before the field's subscriber does is charged once by BaseSharedBuffer and, from the
        // hand-off on, once more by AsFlux until it is delivered; the hand-off itself releases the first charge

        write(streamingBody.sharedBuffer(), "foo=ba");

        RawFormField field1 = fields.queue.remove();
        assertEquals("foo", field1.metadata().name());
        QueueSubscriber<String> data1 = content(field1.byteBody()).noBackpressure();
        assertEquals("ba", data1.queue.poll());

        for (int i = 0; i < 10; i++) {
            write(streamingBody.sharedBuffer(), "ba");
            assertEquals("ba", data1.queue.poll());
        }

        write(streamingBody.sharedBuffer(), "r&fizz=");
        assertEquals("r", data1.queue.poll());
        assertTrue(data1.complete);

        // next field gets 8 bytes and should fail immediately

        RawFormField field2 = fields.queue.remove();
        QueueSubscriber<String> data2 = content(field2.byteBody());
        write(streamingBody.sharedBuffer(), "buzzbuzz");
        data2.request(1);

        assertInstanceOf(BufferLengthExceededException.class, data2.error);
    }

    @Test
    public void formBufferLimit() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder()
            .enableQuirks(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE)
            .forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, new BodySizeLimits(Long.MAX_VALUE, 6), streamingBody.rootBody())
            .fields().subscribe(fields.noBackpressure());

        // note: the 'ba' that arrives before the field's subscriber does is charged once by BaseSharedBuffer and, from the
        // hand-off on, once more by AsFlux until it is delivered; the hand-off itself releases the first charge

        write(streamingBody.sharedBuffer(), "foo=ba");
        RawFormField field1 = fields.queue.remove();
        CloseableByteBody bb1 = field1.byteBody();
        Flux.from(bb1.split(ByteBody.SplitBackpressureMode.FASTEST).toReadBufferPublisher()).subscribe(ReadBuffer::close);

        write(streamingBody.sharedBuffer(), "r&fizz=");
        RawFormField field2 = fields.queue.remove();
        QueueSubscriber<String> data2 = content(field2.byteBody());

        write(streamingBody.sharedBuffer(), "buzz");
        assertInstanceOf(BufferLengthExceededException.class, data2.error);

        bb1.close();
    }

    /**
     * A field whose content spans two writes is devolved to streaming and then buffered whole by its
     * subscriber. The bytes it keeps have to stay charged against the form-wide limit after the
     * field completes, otherwise a client that sends the form slowly can buffer roughly the limit
     * per field instead of the limit per form.
     */
    @Test
    public void formBufferLimitCountsCompletedFieldsSplitAcrossWrites() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder()
            .enableQuirks(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE)
            .forUrlEncodedData(), null, StandardCharsets.UTF_8, channel, BodySizeLimits.UNLIMITED, new BodySizeLimits(Long.MAX_VALUE, 12), streamingBody.rootBody())
            .fields().subscribe(fields.noBackpressure());

        // foo=666666 (6 bytes of content) split across two writes, so the field is devolved to streaming
        write(streamingBody.sharedBuffer(), "foo=666");
        RawFormField field1 = fields.queue.remove();
        AtomicReference<CloseableAvailableByteBody> buffered1 = new AtomicReference<>();
        InternalByteBody.bufferFlow(field1.byteBody()).onComplete((b, e) -> buffered1.set(b));
        write(streamingBody.sharedBuffer(), "666&hello=");
        assertNotNull(buffered1.get(), "field 1 should be buffered once complete");
        assertEquals(6, buffered1.get().length());

        // hello=7777777 (7 bytes) would bring the form to 13 bytes, over the limit of 12, while field 1 is still held
        RawFormField field2 = fields.queue.remove();
        AtomicReference<Throwable> error2 = new AtomicReference<>();
        InternalByteBody.bufferFlow(field2.byteBody()).onComplete((b, e) -> {
            error2.set(e);
            if (b != null) {
                b.close();
            }
        });
        write(streamingBody.sharedBuffer(), "7777777");
        assertInstanceOf(BufferLengthExceededException.class, error2.get());

        buffered1.get().close();
    }

    private static final class MockUpstream implements BufferConsumer.Upstream {
        boolean allowDiscard = false;
        boolean disregardBackpressure = false;
        long consumed = 0;

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            this.consumed = Math.addExact(bytesConsumed, consumed);
        }

        @Override
        public void allowDiscard() {
            allowDiscard = true;
        }

        @Override
        public void disregardBackpressure() {
            disregardBackpressure = true;
        }
    }

    private static final class QueueSubscriber<T> implements Subscriber<T> {
        final Queue<T> queue = new ArrayDeque<>();
        Throwable error;
        boolean complete = false;
        Subscription subscription;

        private Runnable setup = () -> {
        };

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            setup.run();
        }

        public QueueSubscriber<T> noBackpressure() {
            request(Long.MAX_VALUE);
            return this;
        }

        public QueueSubscriber<T> request(long n) {
            if (subscription != null) {
                subscription.request(n);
            } else {
                Runnable r = setup;
                setup = () -> {
                    r.run();
                    subscription.request(n);
                };
            }
            return this;
        }

        public QueueSubscriber<T> cancel() {
            if (subscription != null) {
                subscription.cancel();
            } else {
                Runnable r = setup;
                setup = () -> {
                    r.run();
                    subscription.cancel();
                };
            }
            return this;
        }

        @Override
        public void onNext(T t) {
            queue.add(t);
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
}
