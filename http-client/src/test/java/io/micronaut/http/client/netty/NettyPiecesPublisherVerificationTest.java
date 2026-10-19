package io.micronaut.http.client.netty;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.stream.BodyPublishers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * The Reactive Streams TCK for the pieces of {@code dataStream} and {@code exchangeStream},
 * {@link NettyPiecesPublisher}.
 */
public class NettyPiecesPublisherVerificationTest extends PublisherVerification<ByteBuffer<?>> {

    public NettyPiecesPublisherVerificationTest() {
        super(new TestEnvironment(1000));
    }

    @Override
    public Publisher<ByteBuffer<?>> createPublisher(long elements) {
        BodyPublishers.Unicast<ByteBuf> source = new BodyPublishers.Unicast<>() {
        };
        for (long i = 0; i < elements; i++) {
            source.tryNext(Unpooled.wrappedBuffer(new byte[] {(byte) i}));
        }
        source.tryComplete();
        return new NettyPiecesPublisher(source);
    }

    @Override
    public Publisher<ByteBuffer<?>> createFailedPublisher() {
        BodyPublishers.Unicast<ByteBuf> source = new BodyPublishers.Unicast<>() {
        };
        source.tryError(new IllegalStateException("failed"));
        return new NettyPiecesPublisher(source);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1024;
    }
}
