package io.micronaut.http.body.stream;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * The publisher of the buffers of a body read from an input stream, read one buffer at a time.
 * The reads run on a direct executor, so that the publisher is measured, not a thread hand-off.
 * The stream returns at most {@code chunk} bytes per read, as a socket would.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class InputStreamBodyBenchmark {

    @Param({"1048576"})
    int size;

    @Param({"8192"})
    int chunk;

    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT);
    private byte[] bytes;

    @Setup
    public void setup() {
        bytes = new byte[size];
        if (read() != size) {
            throw new IllegalStateException("Not every byte was read");
        }
    }

    @Benchmark
    public long read() {
        InputStream stream = new ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, chunk));
            }
        };
        OneAtATime reader = new OneAtATime();
        InputStreamByteBody.create(stream, OptionalLong.of(size), Runnable::run, factory).toReadBufferPublisher().subscribe(reader);
        if (!reader.complete) {
            throw new IllegalStateException("The body did not complete");
        }
        return reader.bytes;
    }

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
