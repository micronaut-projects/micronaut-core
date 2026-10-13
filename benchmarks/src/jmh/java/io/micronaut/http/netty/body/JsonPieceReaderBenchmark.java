package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.Book;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.CountingSubscriber;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The elements of a JSON array split into pieces, through the reactive processor as it was, the
 * publisher bridge over the piece reader that {@code readChunked} now returns, and the piece reader
 * directly; and a JSON stream bound as a list, before and after.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonPieceReaderBenchmark {

    private static final Argument<Book> BOOK = Argument.of(Book.class);
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    @Param({"small", "large"})
    String shape;

    private final SimpleHttpHeaders headers = new SimpleHttpHeaders();
    private JsonMapper mapper;
    private NettyJsonHandler<Book> arrayHandler;
    private NettyJsonStreamHandler<List<Book>> streamHandler;
    private byte[][] arrayPieces;
    private byte[] stream;
    private int elements;

    @Setup
    public void setup() {
        mapper = JsonMapper.createDefault();
        arrayHandler = new NettyJsonHandler<>(mapper);
        streamHandler = new NettyJsonStreamHandler<>(mapper);
        List<String> values = PieceReaderBenchmarkSupport.values(shape);
        elements = values.size();
        arrayPieces = PieceReaderBenchmarkSupport.pieces(PieceReaderBenchmarkSupport.array(values), 8192);
        stream = PieceReaderBenchmarkSupport.stream(values);
    }

    @Benchmark
    public int arrayLegacyFluxOneByOne() {
        return subscribe(PieceReaderBenchmarkSupport.legacyArrayElements(arrayHandler, BOOK, headers, PieceReaderBenchmarkSupport.buffers(arrayPieces), Long.MAX_VALUE), true);
    }

    @Benchmark
    public int arrayLegacyFluxUnbounded() {
        return subscribe(PieceReaderBenchmarkSupport.legacyArrayElements(arrayHandler, BOOK, headers, PieceReaderBenchmarkSupport.buffers(arrayPieces), Long.MAX_VALUE), false);
    }

    @Benchmark
    public int arrayBridgeOneByOne() {
        return subscribe(arrayHandler.readChunked(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, PieceReaderBenchmarkSupport.buffers(arrayPieces), Long.MAX_VALUE), true);
    }

    @Benchmark
    public int arrayBridgeUnbounded() {
        return subscribe(arrayHandler.readChunked(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, PieceReaderBenchmarkSupport.buffers(arrayPieces), Long.MAX_VALUE), false);
    }

    @Benchmark
    public int arrayPieceReader() throws IOException {
        int count = 0;
        try (PieceReader<Book> reader = arrayHandler.openPieceReader(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, Long.MAX_VALUE)) {
            for (byte[] piece : arrayPieces) {
                reader.read(READ_BUFFERS.adapt(Unpooled.wrappedBuffer(piece)));
                while (reader.poll() != null) {
                    count++;
                }
            }
            reader.complete();
            while (reader.poll() != null) {
                count++;
            }
        }
        return check(count);
    }

    @Benchmark
    public int streamListLegacyBlock() {
        return check(PieceReaderBenchmarkSupport.legacyStreamList(mapper, BOOK, headers,
            NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer(stream))).size());
    }

    @Benchmark
    public int streamListPieces() {
        return check(streamHandler.read(Argument.listOf(Book.class), MediaType.APPLICATION_JSON_STREAM_TYPE, headers,
            NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer(stream))).size());
    }

    private int subscribe(org.reactivestreams.Publisher<? extends Book> publisher, boolean oneByOne) {
        CountingSubscriber<Book> subscriber = new CountingSubscriber<>(oneByOne);
        publisher.subscribe(subscriber);
        return check(subscriber.count());
    }

    private int check(int count) {
        if (count != elements) {
            throw new IllegalStateException("Expected " + elements + " elements, got " + count);
        }
        return count;
    }
}
