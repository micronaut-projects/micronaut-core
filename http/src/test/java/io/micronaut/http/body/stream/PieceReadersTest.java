package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publisher bridge over a piece reader, the piece reader over a reactive reader, the default
 * {@code readChunked} over a piece reader, and the publisher of {@link BodyElements}.
 */
class PieceReadersTest {

    private static final Headers HEADERS = new SimpleHttpHeaders();

    @Test
    void theBridgeDecodesAnElementWhenItIsRequested() {
        AtomicInteger decoded = new AtomicInteger();
        Publisher<String> lines = PieceReaders.publisher(pieces("a\nb", "\nc\n"), new LineReader(decoded));
        Recorder<String> recorder = new Recorder<>();
        lines.subscribe(recorder);

        recorder.request(1);
        assertEquals(List.of("a"), recorder.elements);
        assertEquals(1, decoded.get());
        recorder.request(2);
        assertEquals(List.of("a", "b", "c"), recorder.elements);
        assertEquals(3, decoded.get());
        // the end without more demand
        assertTrue(recorder.complete);
    }

    @Test
    void theBridgeCompletesWhenTheLastRequestedElementIsTheLast() {
        Recorder<String> recorder = new Recorder<>();
        PieceReaders.publisher(pieces("a\nb\n"), new LineReader(new AtomicInteger())).subscribe(recorder);

        recorder.request(2);
        assertEquals(List.of("a", "b"), recorder.elements);
        assertTrue(recorder.complete);
    }

    @Test
    void cancellingTheBridgeClosesTheReader() {
        LineReader reader = new LineReader(new AtomicInteger());
        Recorder<String> recorder = new Recorder<>();
        PieceReaders.publisher(pieces("a\nb\n", "c\n"), reader).subscribe(recorder);

        recorder.request(1);
        recorder.subscription.cancel();
        assertTrue(reader.closed);
    }

    @Test
    void aFailureOfTheReaderFailsTheBridge() {
        Recorder<String> recorder = new Recorder<>();
        PieceReaders.publisher(pieces("a\n", "boom\n"), new LineReader(new AtomicInteger())).subscribe(recorder);

        recorder.request(Long.MAX_VALUE);
        assertEquals(List.of("a"), recorder.elements);
        assertInstanceOf(CodecException.class, recorder.failure);
    }

    @Test
    void aFailureOfTheInputFailsTheBridge() {
        Recorder<String> recorder = new Recorder<>();
        Publisher<ReadBuffer> input = Flux.concat(pieces("a\n"), Flux.error(new IOException("connection reset")));
        PieceReaders.publisher(input, new LineReader(new AtomicInteger())).subscribe(recorder);

        recorder.request(Long.MAX_VALUE);
        assertInstanceOf(IOException.class, recorder.failure);
    }

    @Test
    void theBridgeHasOneSubscriber() {
        Publisher<String> lines = PieceReaders.publisher(pieces("a\n"), new LineReader(new AtomicInteger()));
        lines.subscribe(new Recorder<>());
        Recorder<String> second = new Recorder<>();
        lines.subscribe(second);
        assertInstanceOf(IllegalStateException.class, second.failure);
    }

    @Test
    void aReaderOfAPublisherIsReadAsPieces() throws IOException {
        PieceReader<String> reader = PieceReaders.open(new FluxLineReader(false), Argument.STRING, MediaType.TEXT_PLAIN_TYPE, HEADERS, 1024);

        reader.read(piece("one"));
        assertEquals("one", reader.poll());
        reader.read(piece("two"));
        reader.complete();
        assertEquals("two", reader.poll());
        assertNull(reader.poll());
        reader.close();
    }

    @Test
    void aReaderOfAPublisherThatEmitsAsynchronouslyFailsLoudly() throws IOException {
        PieceReader<String> reader = PieceReaders.open(new FluxLineReader(true), Argument.STRING, MediaType.TEXT_PLAIN_TYPE, HEADERS, 1024);

        reader.read(piece("one"));
        reader.complete();
        assertThrows(IllegalStateException.class, () -> {
            // the element arrives later on another thread, or not before the end is reported
            for (int i = 0; i < 100; i++) {
                reader.poll();
                Thread.sleep(1);
            }
        });
        reader.close();
    }

    @Test
    void readChunkedIsDerivedFromThePieceReader() {
        ChunkedMessageBodyReader<String> onlyPieces = new OnlyPiecesReader();
        List<String> lines = Flux.<String>from(onlyPieces.readChunked(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, HEADERS,
                Flux.just(buffer("a\nb"), buffer("\n"))))
            .collectList()
            .block();
        assertEquals(List.of("a", "b"), lines);
    }

    @Test
    void aReaderWithNeitherMethodFails() {
        ChunkedMessageBodyReader<String> neither = new ChunkedMessageBodyReader<>() {
            @Override
            public String read(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) {
                return "";
            }
        };
        assertThrows(UnsupportedOperationException.class, () -> neither.readChunked(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, HEADERS, Flux.empty()));
    }

    @Test
    void elementsAsAPublisher() {
        AtomicBoolean closed = new AtomicBoolean();
        BodyElements<Integer> numbers = new CountingElements(3, closed);
        Recorder<Integer> recorder = new Recorder<>();
        new BodyElementsPublisher<>(numbers).subscribe(recorder);

        recorder.request(2);
        assertEquals(List.of(0, 1), recorder.elements);
        recorder.request(5);
        assertEquals(List.of(0, 1, 2), recorder.elements);
        assertTrue(recorder.complete);
    }

    @Test
    void cancellingThePublisherOfElementsClosesThem() {
        AtomicBoolean closed = new AtomicBoolean();
        Recorder<Integer> recorder = new Recorder<>();
        new BodyElementsPublisher<>(new CountingElements(3, closed)).subscribe(recorder);

        recorder.request(1);
        recorder.subscription.cancel();
        assertTrue(closed.get());
    }

    private static Flux<ReadBuffer> pieces(String... pieces) {
        return Flux.fromArray(pieces).map(PieceReadersTest::piece);
    }

    private static ReadBuffer piece(String text) {
        return ReadBufferFactory.getJdkFactory().adapt(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteBuffer<?> buffer(String text) {
        return piece(text).toByteBuffer();
    }

    /**
     * Lines, decoded when they are polled; "boom" does not decode.
     */
    private static final class LineReader implements PieceReader<String> {
        private final AtomicInteger decoded;
        private final StringBuilder pending = new StringBuilder();
        private final List<String> lines = new ArrayList<>();
        boolean closed;

        LineReader(AtomicInteger decoded) {
            this.decoded = decoded;
        }

        @Override
        public void read(ReadBuffer piece) {
            try (piece) {
                pending.append(piece.toString(StandardCharsets.UTF_8));
            }
            int end;
            while ((end = pending.indexOf("\n")) >= 0) {
                lines.add(pending.substring(0, end));
                pending.delete(0, end + 1);
            }
        }

        @Override
        public void complete() {
        }

        @Override
        public @Nullable String poll() {
            if (lines.isEmpty()) {
                return null;
            }
            String line = lines.remove(0);
            if (line.equals("boom")) {
                throw new CodecException("Cannot decode " + line);
            }
            decoded.incrementAndGet();
            return line;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * A reader of a publisher only: one string per buffer, emitted at once or on another thread.
     */
    private static final class FluxLineReader implements ChunkedMessageBodyReader<String> {
        private final boolean async;

        FluxLineReader(boolean async) {
            this.async = async;
        }

        @Override
        public Publisher<? extends String> readChunked(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            Flux<String> strings = Flux.from(input).map(buffer -> buffer.toString(StandardCharsets.UTF_8));
            return async ? strings.publishOn(Schedulers.single()) : strings;
        }

        @Override
        public String read(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) {
            return "";
        }
    }

    /**
     * A reader that implements only the piece reader: lines.
     */
    private static final class OnlyPiecesReader implements ChunkedMessageBodyReader<String> {
        @Override
        public PieceReader<String> openPieceReader(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, long maxElementSize) {
            return new LineReader(new AtomicInteger());
        }

        @Override
        public String read(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) {
            return "";
        }
    }

    /**
     * The numbers below a count.
     */
    private static final class CountingElements implements BodyElements<Integer> {
        private final int count;
        private final AtomicBoolean closed;
        private int next;

        CountingElements(int count, AtomicBoolean closed) {
            this.count = count;
            this.closed = closed;
        }

        @Override
        public CompletionStage<Optional<Integer>> next() {
            return CompletableFuture.completedStage(next < count ? Optional.of(next++) : Optional.empty());
        }

        @Override
        public CompletionStage<Void> forEach(Function<? super Integer, ? extends CompletionStage<?>> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            close();
            return CompletableFuture.completedStage(null);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class Recorder<T> implements Subscriber<T> {
        final List<T> elements = new ArrayList<>();
        Subscription subscription;
        boolean complete;
        Throwable failure;

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
        }

        void request(long n) {
            subscription.request(n);
        }

        @Override
        public void onNext(T t) {
            elements.add(t);
        }

        @Override
        public void onError(Throwable t) {
            failure = t;
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
