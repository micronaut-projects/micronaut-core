package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.form.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.DecoderQuirk;
import io.netty.contrib.multipart.FormDecoderException;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new FormDemuxer(PostBodyDecoder.builder().forUrlEncodedData(), channel, BodySizeLimits.UNLIMITED, body);
    }

    private static <T> Queue<T> toQueue(Flux<T> flux) {
        QueueSubscriber<T> subscriber = new QueueSubscriber<>();
        subscriber.noBackpressure();
        flux.subscribe(subscriber);
        return subscriber.queue;
    }

    private void write(BaseSharedBuffer dst, String msg) {
        dst.add(byteBodyFactory.readBufferFactory().copyOf(msg, StandardCharsets.UTF_8));
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
        assertInstanceOf(FormDecoderException.class, fields.error);
    }

    @Test
    public void parseError2() {
        MockUpstream upstream = new MockUpstream();
        ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        QueueSubscriber<RawFormField> fields = new QueueSubscriber<>();
        new FormDemuxer(PostBodyDecoder.builder()
            .enableQuirks(DecoderQuirk.REFUSE_NON_HEX_PERCENT_DECODE)
            .forUrlEncodedData(), channel, BodySizeLimits.UNLIMITED, streamingBody.rootBody()).fields().subscribe(fields.noBackpressure());

        write(streamingBody.sharedBuffer(), "foo=ba");

        RawFormField field = fields.queue.remove();
        assertEquals("foo", field.metadata().name());
        QueueSubscriber<String> data = content(field.byteBody()).noBackpressure();
        assertEquals("ba", data.queue.poll());

        write(streamingBody.sharedBuffer(), "%rr");

        assertInstanceOf(FormDecoderException.class, data.error);
        assertInstanceOf(FormDecoderException.class, fields.error);
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
