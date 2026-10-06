package io.micronaut.http.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.CountingSubscriber;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A reader of one element per piece (the {@code String} reader): its {@code Flux.map}
 * {@code readChunked}, and its piece reader behind the publisher bridge that a reader without its
 * own {@code readChunked} gets by default.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OnePiecePerElementBenchmark {

    private static final int PIECES = 1000;

    private final SimpleHttpHeaders headers = new SimpleHttpHeaders();
    private StringBodyReader reader;
    private byte[][] pieces;

    @Setup
    public void setup() {
        reader = new StringBodyReader(new ApplicationConfiguration());
        pieces = new byte[PIECES][];
        for (int i = 0; i < PIECES; i++) {
            pieces[i] = ("piece number " + i + " ").repeat(16).getBytes(StandardCharsets.UTF_8);
        }
    }

    private Flux<io.micronaut.core.io.buffer.ByteBuffer<?>> input() {
        return Flux.fromArray(pieces).map(piece -> NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer(piece)));
    }

    @Benchmark
    public int fluxMap() {
        CountingSubscriber<String> subscriber = new CountingSubscriber<>(false);
        reader.readChunked(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, headers, input()).subscribe(subscriber);
        return subscriber.count();
    }

    @Benchmark
    public int pieceReaderBridge() {
        CountingSubscriber<String> subscriber = new CountingSubscriber<>(false);
        io.micronaut.http.body.stream.PieceReaders.publisherOfBuffers(input(),
            reader.openPieceReader(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, headers, Long.MAX_VALUE),
            io.micronaut.buffer.netty.NettyReadBufferFactory.of(io.netty.buffer.ByteBufAllocator.DEFAULT)::adapt).subscribe(subscriber);
        return subscriber.count();
    }
}
