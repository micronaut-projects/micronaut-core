package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Inputs of the piece reader benchmarks, and the reactive JSON reading of the Netty handlers as it
 * was before the piece readers, to compare against.
 */
public final class PieceReaderBenchmarkSupport {

    /**
     * The element type of the benchmarks: a class, as Jackson creates a record with
     * {@code MethodHandle.invokeWithArguments}, which costs more than the reading measured here.
     */
    public static final class Book {
        public int id;
        public String title;
        public int pages;
    }

    private PieceReaderBenchmarkSupport() {
    }

    /**
     * @param shape {@code small}: many small elements, {@code large}: few large elements
     * @return The elements as JSON values
     */
    public static List<String> values(String shape) {
        List<String> values = new ArrayList<>();
        if ("small".equals(shape)) {
            for (int i = 0; i < 2000; i++) {
                values.add("{\"id\":" + i + ",\"title\":\"Book number " + i + "\",\"pages\":" + (100 + i) + "}");
            }
        } else {
            String title = "x".repeat(4096);
            for (int i = 0; i < 50; i++) {
                values.add("{\"id\":" + i + ",\"title\":\"" + title + "\",\"pages\":" + (100 + i) + "}");
            }
        }
        return values;
    }

    /**
     * @param values The values
     * @return A JSON array of the values
     */
    public static byte[] array(List<String> values) {
        return ("[" + String.join(",", values) + "]").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param values The values
     * @return A JSON stream of the values
     */
    public static byte[] stream(List<String> values) {
        return (String.join("\n", values) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param body      The body
     * @param pieceSize The size of a piece
     * @return The pieces of the body
     */
    public static byte[][] pieces(byte[] body, int pieceSize) {
        List<byte[]> pieces = new ArrayList<>();
        for (int i = 0; i < body.length; i += pieceSize) {
            pieces.add(Arrays.copyOfRange(body, i, Math.min(body.length, i + pieceSize)));
        }
        return pieces.toArray(new byte[0][]);
    }

    /**
     * @param pieces The pieces
     * @return The pieces as Netty buffers, as the input of a chunked reader
     */
    public static Flux<ByteBuffer<?>> buffers(byte[][] pieces) {
        return Flux.fromArray(pieces).map(piece -> NettyByteBufferFactory.DEFAULT.wrap(Unpooled.wrappedBuffer(piece)));
    }

    /**
     * The reactive reading of the elements of a JSON array of the Netty JSON handler before the
     * piece readers: the processor splits a {@link Flux} of buffers.
     *
     * @param handler        The handler, which decodes an element
     * @param type           The element type
     * @param headers        The headers
     * @param input          The input
     * @param maxElementSize The limit of an element
     * @param <T>            The element type
     * @return The elements
     */
    public static <T> Publisher<T> legacyArrayElements(NettyJsonHandler<T> handler, Argument<T> type, Headers headers, Publisher<ByteBuffer<?>> input, long maxElementSize) {
        JsonChunkedProcessor processor = new JsonChunkedProcessor(maxElementSize);
        processor.counter.unwrapTopLevelArray();
        return processor.process(Flux.from(input).map(JsonChunkedProcessor::nettyBuffer))
            .map(bb -> JsonChunkedProcessor.readReleasing(bb, value -> handler.read(type, MediaType.APPLICATION_JSON_TYPE, headers, value)));
    }

    /**
     * The reading of a JSON stream bound as a list before the piece readers: the elements of the
     * reactive reader, collected by blocking.
     *
     * @param mapper The mapper
     * @param type   The element type
     * @param body   The body
     * @param <T>    The element type
     * @return The elements
     */
    public static <T> List<T> legacyStreamList(JsonMapper mapper, Argument<T> type, Headers headers, ByteBuffer<?> body) {
        JsonMessageHandler<T> handler = new JsonMessageHandler<>(mapper);
        JsonChunkedProcessor processor = new JsonChunkedProcessor();
        return Objects.requireNonNull(processor.process(Flux.just(body).map(JsonChunkedProcessor::nettyBuffer))
            .map(bb -> JsonChunkedProcessor.readReleasing(bb, value -> handler.read(type, MediaType.APPLICATION_JSON_STREAM_TYPE, headers, value)))
            .collectList()
            .block());
    }

    /**
     * A subscriber that requests one element at a time, like a cursor, or all of them.
     *
     * @param <T> The element type
     */
    public static final class CountingSubscriber<T> implements Subscriber<T> {
        private final boolean oneByOne;
        private Subscription subscription;
        private int count;
        private boolean complete;
        private Throwable failure;

        /**
         * @param oneByOne Whether one element is requested at a time
         */
        public CountingSubscriber(boolean oneByOne) {
            this.oneByOne = oneByOne;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(oneByOne ? 1 : Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            count++;
            if (oneByOne) {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            failure = t;
        }

        @Override
        public void onComplete() {
            complete = true;
        }

        /**
         * @return The number of elements, once the publisher completed synchronously
         */
        public int count() {
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            if (!complete) {
                throw new IllegalStateException("not complete");
            }
            return count;
        }
    }
}
