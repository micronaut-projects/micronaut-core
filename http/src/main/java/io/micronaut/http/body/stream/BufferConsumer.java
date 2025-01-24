/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
         * Called when a number of bytes has been consumed by the downstream. Note that this can
         * exceed the actual number of bytes written so far, if the downstream wants to signal it
         * is ready consume much more data.
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
