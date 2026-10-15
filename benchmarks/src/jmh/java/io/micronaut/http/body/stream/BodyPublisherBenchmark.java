package io.micronaut.http.body.stream;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.InternalByteBody;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.TimeUnit;

/**
 * The publisher of the buffers of a streaming body, read one buffer at a time as the readers of
 * the elements of a body do. The source adds the next piece once the previous one was consumed,
 * as a connection reads the next bytes. {@code buffered} is the publisher that is held to the
 * buffer limit of the body, {@code unbuffered} the one a reader with limits of its own uses.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BodyPublisherBenchmark {

    @Param({"buffered", "unbuffered"})
    String mode;

    @Param({"1000"})
    int pieces;

    @Param({"64"})
    int pieceSize;

    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT);
    private byte[] piece;

    @Setup
    public void setup() {
        piece = new byte[pieceSize];
        if (read() != (long) pieces * pieceSize) {
            throw new IllegalStateException("Not every byte was read");
        }
    }

    @Benchmark
    public long read() {
        Source source = new Source(piece, pieces);
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(BodySizeLimits.UNLIMITED, source);
        source.sharedBuffer = body.sharedBuffer();
        Publisher<ReadBuffer> publisher = "buffered".equals(mode)
            ? body.rootBody().toReadBufferPublisher()
            : InternalByteBody.toUnbufferedReadBufferPublisher(body.rootBody());
        OneAtATime reader = new OneAtATime();
        publisher.subscribe(reader);
        if (!reader.complete) {
            throw new IllegalStateException("The body did not complete");
        }
        return reader.bytes;
    }

    /**
     * Adds the next piece once the bytes of the previous one were consumed.
     */
    private static final class Source implements BufferConsumer.Upstream {
        private final byte[] piece;
        private final int pieces;
        private @Nullable BaseSharedBuffer sharedBuffer;
        private int added;

        Source(byte[] piece, int pieces) {
            this.piece = piece;
            this.pieces = pieces;
        }

        @Override
        public void start() {
            next();
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            next();
        }

        private void next() {
            BaseSharedBuffer buffer = sharedBuffer;
            if (buffer == null || added > pieces) {
                return;
            }
            if (added++ < pieces) {
                buffer.add(ReadBufferFactory.getJdkFactory().adapt(piece));
            } else {
                buffer.complete();
            }
        }
    }

    /**
     * Requests the next buffer once it has read one.
     */
    private static final class OneAtATime implements Subscriber<ReadBuffer> {
        private @Nullable Subscription subscription;
        private long bytes;
        private boolean complete;

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            try (buffer) {
                bytes += buffer.readable();
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable t) {
            throw new IllegalStateException(t);
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
