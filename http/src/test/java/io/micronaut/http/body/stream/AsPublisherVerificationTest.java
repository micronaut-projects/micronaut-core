package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * The Reactive Streams TCK for the publisher of the buffers of a streaming body,
 * {@link BaseSharedBuffer.AsPublisher}.
 */
public class AsPublisherVerificationTest extends PublisherVerification<ReadBuffer> {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    public AsPublisherVerificationTest() {
        super(new TestEnvironment(1000));
    }

    @Override
    public Publisher<ReadBuffer> createPublisher(long elements) {
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // The finite TCK fixture has already supplied all bytes; no producer needs demand.
            }
        });
        Publisher<ReadBuffer> publisher = body.rootBody().toReadBufferPublisher();
        for (long i = 0; i < elements; i++) {
            body.sharedBuffer().add(ReadBufferFactory.getJdkFactory().adapt(new byte[] {(byte) i}));
        }
        body.sharedBuffer().complete();
        return publisher;
    }

    @Override
    public Publisher<ReadBuffer> createFailedPublisher() {
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // The failed TCK fixture has no producer to notify about consumption.
            }
        });
        Publisher<ReadBuffer> publisher = body.rootBody().toReadBufferPublisher();
        body.sharedBuffer().error(new IllegalStateException("failed"));
        return publisher;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1024;
    }
}
