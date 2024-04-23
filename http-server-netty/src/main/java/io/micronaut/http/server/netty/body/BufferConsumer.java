package io.micronaut.http.server.netty.body;

import io.netty.buffer.ByteBuf;

public interface BufferConsumer {
    void add(ByteBuf buf);

    void complete();

    /**
     * This interface manages the backpressure for data consumptions. It is highly concurrent:
     * Calls to {@link #onBytesConsumed(long)} may happen at the same time on different threads.
     */
    interface Upstream {
        Upstream IGNORE = new Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            public void discard() {
            }
        };

        /**
         * Called when a number of bytes has been consumed by the downstream.
         *
         * @param bytesConsumed The number of bytes that were consumed
         */
        void onBytesConsumed(long bytesConsumed);

        void discard();
    }
}
