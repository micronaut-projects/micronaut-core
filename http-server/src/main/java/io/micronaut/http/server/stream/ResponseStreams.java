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
import io.micronaut.http.body.ChunkSource;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.sse.SseEmitter;
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

    /**
     * The response attribute that disables the compression of the response body: the events of
     * a server-sent events response must reach the client as they are sent.
     */
    public static final String COMPRESSION_DISABLED = "micronaut.http.server.response.compression.disabled";

    /**
     * The high-water mark of a streamed body, in bytes.
     */
    static final int DEFAULT_HIGH_WATER_MARK = SseEmitter.DEFAULT_HIGH_WATER_MARK;

    private static final Logger LOG = LoggerFactory.getLogger(ResponseStreams.class);

    private ResponseStreams() {
    }

    /**
     * Stream the elements of a source. The source is pulled one element at a time, while the
     * connection keeps up.
     *
     * @param factory The body factory of the response
     * @param source  The source
     * @param encoder Encodes the elements
     * @return Completes with the body once the first element (or the end) is available, or
     * exceptionally if producing or encoding the first element failed: nothing was sent then
     */
    public static ExecutionFlow<CloseableByteBody> stream(ByteBodyFactory factory,
                                                          ChunkSource<?> source,
                                                          ElementEncoder encoder) {
        return ChunkSourceBody.start(factory, source, encoder);
    }

    /**
     * Close a source whose elements are not written, e.g. the body of the response to a HEAD
     * request.
     *
     * @param source The source
     */
    public static void discard(ChunkSource<?> source) {
        try {
            source.close();
        } catch (Throwable e) {
            LOG.warn("Failed to close the chunk source of a response", e);
        }
    }

    /**
     * Encodes the elements of a streamed body.
     */
    public interface ElementEncoder {
        /**
         * @param element The element
         * @param prefix  The bytes to write before the element, e.g. a separator
         * @return The bytes
         * @throws Exception If the element cannot be encoded
         */
        ReadBuffer encode(Object element, byte @Nullable [] prefix) throws Exception;

        /**
         * Whether the elements are framed as a JSON array, decided on the first element.
         *
         * @param firstElement The first element, or {@code null} if there is none
         * @return {@code true} to write the elements as a JSON array
         */
        boolean jsonArray(@Nullable Object firstElement);
    }
}
