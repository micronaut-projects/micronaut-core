package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;

@Internal
public interface BufferConsumer {
    /**
     * Consume a buffer. Release ownership is transferred to this consumer.
     *
     * @param buf The buffer to consume
     */
    void add(ByteBuf buf);

    /**
     * Signal completion of the stream.
     */
    void complete();

    /**
     * This interface manages the backpressure for data consumptions. It is highly concurrent:
     * Calls to {@link #onBytesConsumed(long)} may happen at the same time on different threads.
     */
    interface Upstream {
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
        void allowDiscard();
    }
}
