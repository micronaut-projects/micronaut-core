/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseElements;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streamed response bodies without Reactive Streams: the entry point of the response encoding
 * to the bodies of this package.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ResponseStreams {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseStreams.class);

    private ResponseStreams() {
    }

    /**
     * Stream the elements of a {@link ResponseElements} body, pulled one element at a time while
     * the connection keeps up.
     *
     * @param factory       The body factory of the response
     * @param elements      The elements
     * @param encoder       Encodes the elements
     * @param highWaterMark The high-water mark of the stream, in bytes
     * @return Completes with the body once the first element (or the end) is available, or
     * exceptionally if producing or encoding the first element failed: nothing was sent then
     */
    public static ExecutionFlow<CloseableByteBody> stream(ByteBodyFactory factory,
                                                          ResponseElements<?> elements,
                                                          ElementEncoder encoder,
                                                          int highWaterMark) {
        return ResponseElementsBody.start(factory, elements, encoder, highWaterMark);
    }

    /**
     * Close elements that are not written, e.g. the body of the response to a HEAD request.
     *
     * @param elements The elements
     */
    public static void discard(ResponseElements<?> elements) {
        try {
            elements.close();
        } catch (Throwable e) {
            LOG.warn("Failed to close the elements of a response", e);
        }
    }

    /**
     * Encodes the elements of a streamed body, like the elements of a {@code Publisher} body.
     */
    public interface ElementEncoder {
        /**
         * Encode an element, with the bytes that precede it, e.g. the separator of a JSON array.
         * Called one element at a time, in order.
         *
         * @param element The element
         * @return The bytes of the element, possibly encoded on another thread, e.g. by a
         * blocking writer
         */
        ExecutionFlow<CloseableByteBody> encode(Object element);

        /**
         * The bytes that end the body, e.g. the end of a JSON array.
         *
         * @param none Whether there was no element
         * @return The bytes, or {@code null} if there are none
         */
        @Nullable ReadBuffer end(boolean none);

        /**
         * Release what the encoder holds: the body ended, failed, or the client disconnected.
         */
        default void close() {
        }
    }
}
