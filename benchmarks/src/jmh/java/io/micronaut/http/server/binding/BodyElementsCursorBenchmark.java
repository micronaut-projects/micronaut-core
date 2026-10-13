package io.micronaut.http.server.binding;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.Book;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
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
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The {@code AsyncRequestBody.elements} cursor over a streaming body, as it was (a cursor over the
 * publisher of the reactive JSON reader) and with the piece reader.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BodyElementsCursorBenchmark {

    private static final Argument<Book> BOOK = Argument.of(Book.class);

    @Param({"small", "large"})
    String shape;

    private final SimpleHttpHeaders headers = new SimpleHttpHeaders();
    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT);
    private NettyJsonHandler<Book> handler;
    private byte[][] pieces;
    private int elements;

    @Setup
    public void setup() {
        handler = new NettyJsonHandler<>(JsonMapper.createDefault());
        List<String> values = PieceReaderBenchmarkSupport.values(shape);
        elements = values.size();
        pieces = PieceReaderBenchmarkSupport.pieces(PieceReaderBenchmarkSupport.array(values), 8192);
    }

    private CloseableByteBody body() {
        return factory.adapt(Flux.fromArray(pieces).map(piece -> factory.readBufferFactory().adapt(piece.clone())));
    }

    @Benchmark
    public int legacyPublisherCursor() {
        CloseableByteBody body = body();
        BodyElements<Book> books = new PublisherBodyElements<>(() -> PieceReaderBenchmarkSupport.legacyArrayElements(handler, BOOK, headers, byteBuffers(body), Long.MAX_VALUE), body::close);
        return read(books);
    }

    @Benchmark
    public int pieceReaderCursor() {
        CloseableByteBody body = body();
        BodyElements<Book> books = new ByteBodyElements<>(body, handler.openPieceReader(BOOK, MediaType.APPLICATION_JSON_TYPE, headers, Long.MAX_VALUE), Function.identity());
        return read(books);
    }

    private static Publisher<ByteBuffer<?>> byteBuffers(CloseableByteBody body) {
        return Flux.from(InternalByteBody.toUnbufferedReadBufferPublisher(body))
            .doOnDiscard(ReadBuffer.class, ReadBuffer::close)
            .map(rb -> {
                try (rb) {
                    return rb.toByteBuffer();
                }
            });
    }

    private int read(BodyElements<Book> books) {
        int count = 0;
        while (true) {
            Optional<Book> book = books.next().toCompletableFuture().join();
            if (book.isEmpty()) {
                break;
            }
            count++;
        }
        books.close();
        if (count != elements) {
            throw new IllegalStateException("Expected " + elements + " elements, got " + count);
        }
        return count;
    }
}
