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
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseElements;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
     * Encodes the elements of a streamed body.
     */
    public interface ElementEncoder {
        /**
         * @param element The element
         * @param framing The framing of the elements, see {@link #framing}
         * @param first   Whether it is the first element
         * @return The bytes of the element, after the bytes of the framing that precede it
         * @throws Exception If the element cannot be encoded
         */
        ReadBuffer encode(Object element, Framing framing, boolean first) throws Exception;

        /**
         * The framing of the elements, decided on the first element.
         *
         * @param firstElement The first element, or {@code null} if there is none
         * @return The framing
         */
        Framing framing(@Nullable Object firstElement);
    }

    /**
     * The bytes around and between the elements of a streamed body.
     */
    public enum Framing {
        /**
         * The elements one after the other.
         */
        NONE(null, null, null, null),
        /**
         * The elements of a JSON array: {@code [a,b]}, and {@code []} for none.
         */
        JSON_ARRAY("[", ",", "]", "[]");

        private final byte @Nullable [] open;
        private final byte @Nullable [] separator;
        private final byte @Nullable [] close;
        private final byte @Nullable [] empty;

        Framing(@Nullable String open, @Nullable String separator, @Nullable String close, @Nullable String empty) {
            this.open = bytes(open);
            this.separator = bytes(separator);
            this.close = bytes(close);
            this.empty = bytes(empty);
        }

        /**
         * Write the bytes that precede an element.
         *
         * @param out   The output of the element
         * @param first Whether it is the first element
         * @throws IOException If writing fails
         */
        public void writePrefix(OutputStream out, boolean first) throws IOException {
            byte[] prefix = first ? open : separator;
            if (prefix != null) {
                out.write(prefix);
            }
        }

        /**
         * The bytes that end the body.
         *
         * @param factory The buffer factory
         * @param none    Whether there was no element
         * @return The bytes, or {@code null} if there are none
         */
        @Nullable ReadBuffer end(ReadBufferFactory factory, boolean none) {
            byte[] end = none ? empty : close;
            return end == null ? null : factory.copyOf(ByteBuffer.wrap(end));
        }

        private static byte @Nullable [] bytes(@Nullable String text) {
            return text == null ? null : text.getBytes(StandardCharsets.UTF_8);
        }
    }
}
