package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.ByteBody;

/**
 * This is a reactor-like API for streaming bytes. It's a bit better than reactor because it's more
 * explicit about reference counting semantics, has more fine-grained controls for cancelling, and
 * has more relaxed concurrency semantics.<br>
 * This interface is buffer type agnostic. For specific buffer types (e.g. netty {@code ByteBuf})
 * there is a specific subinterface.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public interface BufferConsumer {
    /**
     * Signal normal completion of the stream.
     */
    void complete();

    /**
     * Signal that the upstream has discarded the remaining data, as requested by {@link Upstream#allowDiscard()}.
     */
    default void discard() {
        error(ByteBody.BodyDiscardedException.create());
    }

    /**
     * Signal an upstream error.
     *
     * @param e The error
     */
    void error(Throwable e);

    /**
     * This interface manages the backpressure for data consumptions. It is highly concurrent:
     * Calls to {@link #onBytesConsumed(long)} may happen at the same time on different threads.
     */
    interface Upstream {
        /**
         * Signal that we want to start consuming bytes. This is an optional hint to the upstream,
         * the upstream may ignore it and send bytes immediately. This is used for CONTINUE
         * support.
         */
        default void start() {
        }

        /**
         * Called when a number of bytes has been consumed by the downstream.
         *
         * @param bytesConsumed The number of bytes that were consumed
         */
        void onBytesConsumed(long bytesConsumed);

        /**
         * Allow the upstream to discard any further messages. Note that this does not actually
         * mean the messages must be discarded: If another consumer still needs the body data, it
         * may continue to be read and continue to be forwarded to this consumer.
         */
        default void allowDiscard() {
        }

        /**
         * Instruct the upstream to ignore backpressure from this consumer. This is slightly
         * different from {@code onBytesConsumed(Long.MAX_VALUE)}: If there are two consumers
         * in {@link ByteBody.SplitBackpressureMode#FASTEST} mode,
         * a MAX_VALUE requests all data from the common upstream, while a disregardBackpressure
         * removes this downstream from consideration.
         */
        default void disregardBackpressure() {
        }
    }
}
