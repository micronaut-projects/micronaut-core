package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.client.sse.EventStreams;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.Book;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.CountingSubscriber;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;
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
import org.openjdk.jmh.annotations.Warmup;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The events of an event stream split into pieces: the line splitter and the event decoder of the
 * reactive client as they were, the piece reader of the async client directly, and the piece
 * reader behind the publisher bridge.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SseDecodingBenchmark {

    private static final Argument<Book> BOOK = Argument.of(Book.class);
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    @Param({"small", "large"})
    String shape;

    /**
     * The pieces as heap buffers, or as direct buffers, as the Netty client receives them.
     */
    @Param({"heap", "direct"})
    String buffers;

    private final SimpleHttpHeaders headers = new SimpleHttpHeaders();
    private MessageBodyHandlerRegistry registry;
    private ByteBuf[] pieces;
    private int elements;

    @Setup
    public void setup() {
        // the JSON handler for every type: an application context of this module starts GraalPy
        NettyJsonHandler<Object> json = new NettyJsonHandler<>(JsonMapper.createDefault());
        registry = new MessageBodyHandlerRegistry() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, @Nullable List<MediaType> mediaType) {
                return Optional.of((MessageBodyReader<T>) (MessageBodyReader<?>) json);
            }

            @Override
            public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
                return Optional.empty();
            }
        };
        List<String> values = PieceReaderBenchmarkSupport.values(shape);
        elements = values.size();
        StringBuilder stream = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            stream.append("id: ").append(i).append('\n').append("data: ").append(values.get(i)).append("\n\n");
        }
        byte[][] arrays = PieceReaderBenchmarkSupport.pieces(stream.toString().getBytes(StandardCharsets.UTF_8), 8192);
        pieces = new ByteBuf[arrays.length];
        for (int i = 0; i < arrays.length; i++) {
            pieces[i] = "direct".equals(buffers) ? Unpooled.directBuffer(arrays[i].length).writeBytes(arrays[i]) : Unpooled.wrappedBuffer(arrays[i]);
        }
    }

    @Benchmark
    public int legacySplitterAndDecoder() {
        MessageBodyReader<Book> reader = registry.getReader(BOOK, List.of(MediaType.APPLICATION_JSON_TYPE));
        SseEventDecoder decoder = new SseEventDecoder(Long.MAX_VALUE);
        Flux<Event<Book>> events = SseSplitter.split(Flux.fromArray(pieces).map(ByteBuf::retainedDuplicate), BodySizeLimits.UNLIMITED)
            .concatMapIterable(line -> {
                try {
                    return decoder.line(line);
                } finally {
                    line.release();
                }
            })
            .map(event -> Event.of(event, reader.read(BOOK, MediaType.APPLICATION_JSON_TYPE, headers,
                NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer(event.getData())))));
        CountingSubscriber<Event<Book>> subscriber = new CountingSubscriber<>(false);
        events.subscribe(subscriber);
        return check(subscriber.count());
    }

    @Benchmark
    public int pieceReader() throws IOException {
        return read(EventStreams.reader(registry, BOOK, headers, Long.MAX_VALUE));
    }

    private int read(PieceReader<Event<Book>> pieceReader) throws IOException {
        int count = 0;
        try (PieceReader<Event<Book>> reader = pieceReader) {
            for (ByteBuf piece : pieces) {
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

    @Benchmark
    public int pieceReaderBridge() {
        CountingSubscriber<Event<Book>> subscriber = new CountingSubscriber<>(false);
        PieceReaders.publisher(Flux.fromArray(pieces).map(piece -> READ_BUFFERS.adapt(piece.retainedDuplicate())),
            EventStreams.reader(registry, BOOK, headers, Long.MAX_VALUE)).subscribe(subscriber);
        return check(subscriber.count());
    }

    private int check(int count) {
        if (count != elements) {
            throw new IllegalStateException("Expected " + elements + " elements, got " + count);
        }
        return count;
    }
}
