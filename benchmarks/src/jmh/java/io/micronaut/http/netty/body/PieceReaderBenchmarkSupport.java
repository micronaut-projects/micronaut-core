package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.netty.buffer.Unpooled;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
