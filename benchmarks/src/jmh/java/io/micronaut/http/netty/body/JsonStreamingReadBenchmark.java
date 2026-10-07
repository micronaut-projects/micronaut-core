package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.Book;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.CountingSubscriber;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.micronaut.json.body.JsonStreamMessageHandler;
import io.netty.buffer.ByteBuf;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The reading of streamed JSON, the elements of an array and the values of a JSON stream, by the
 * JSON handlers of json-core, without Netty, compared with the Netty processor they replace.
 *
 * <ul>
 *     <li>{@code smallManyPieces}: 2000 elements of about 60 bytes, in pieces of 256 bytes</li>
 *     <li>{@code largeSpanning}: 50 elements of about 4 KiB, in pieces of 512 bytes: each
 *     element spans about eight pieces</li>
 *     <li>{@code singleArray}: the 2000 small elements in one piece</li>
 * </ul>
 *
 * The pieces are heap or direct Netty buffers, as a Netty server delivers them.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class JsonStreamingReadBenchmark {

    private static final Argument<Book> BOOK = Argument.of(Book.class);
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    @Param({"netty", "generic"})
    String implementation;

    @Param({"smallManyPieces", "largeSpanning", "singleArray"})
    String scenario;

    @Param({"heap", "direct"})
    String memory;

    private final SimpleHttpHeaders headers = new SimpleHttpHeaders();
    private ChunkedMessageBodyReader<Book> arrayReader;
    private MessageBodyReader<List<Book>> streamReader;
    private ByteBuf[] arrayPieces;
    private ByteBuf stream;
    private int elements;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() {
        JsonMapper mapper = JsonMapper.createDefault();
        if ("netty".equals(implementation)) {
            arrayReader = new NettyJsonHandler<>(mapper);
            streamReader = new NettyJsonStreamHandler<>(mapper);
        } else {
            arrayReader = new JsonMessageHandler<>(mapper);
            streamReader = new JsonStreamMessageHandler<>(mapper);
        }
        List<String> values = PieceReaderBenchmarkSupport.values("largeSpanning".equals(scenario) ? "large" : "small");
        elements = values.size();
        byte[] array = PieceReaderBenchmarkSupport.array(values);
        int pieceSize = switch (scenario) {
            case "smallManyPieces" -> 256;
            case "largeSpanning" -> 512;
            default -> array.length;
        };
        byte[][] pieces = PieceReaderBenchmarkSupport.pieces(array, pieceSize);
        arrayPieces = new ByteBuf[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            arrayPieces[i] = buffer(pieces[i]);
        }
        stream = buffer(PieceReaderBenchmarkSupport.stream(values));
    }

    private ByteBuf buffer(byte[] bytes) {
        if ("direct".equals(memory)) {
            return Unpooled.directBuffer(bytes.length).writeBytes(bytes);
        }
        return Unpooled.wrappedBuffer(bytes);
    }

    @TearDown
    public void tearDown() {
        for (ByteBuf piece : arrayPieces) {
            piece.release();
        }
        stream.release();
    }

    /**
     * The elements of an array, pulled through the piece reader.
     */
    @Benchmark
    public int arrayPieceReader() throws IOException {
        int count = 0;
        try (PieceReader<Book> reader = arrayReader.openPieceReader(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, Long.MAX_VALUE)) {
            for (ByteBuf piece : arrayPieces) {
                reader.read(READ_BUFFERS.adapt(piece.retainedDuplicate()));
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

    /**
     * The elements of an array, read as a publisher of the buffers of the input.
     */
    @Benchmark
    public int arrayReadChunked() {
        Flux<ByteBuffer<?>> input = Flux.fromArray(arrayPieces).map(piece -> NettyByteBufferFactory.DEFAULT.wrap(piece.retainedDuplicate()));
        CountingSubscriber<Book> subscriber = new CountingSubscriber<>(false);
        arrayReader.readChunked(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, input, Long.MAX_VALUE).subscribe(subscriber);
        return check(subscriber.count());
    }

    /**
     * A JSON stream in one buffer, bound as a list.
     */
    @Benchmark
    public int streamList() {
        return check(streamReader.read(Argument.listOf(Book.class), MediaType.APPLICATION_JSON_STREAM_TYPE, headers,
            NettyByteBufferFactory.DEFAULT.wrap(stream.retainedDuplicate())).size());
    }

    private int check(int count) {
        if (count != elements) {
            throw new IllegalStateException("Expected " + elements + " elements, got " + count);
        }
        return count;
    }
}
