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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

/**
 * Limit for the size of a single node of a streamed JSON body read by
 * {@link NettyJsonHandler#readChunked} or {@link NettyJsonStreamHandler#readChunked}. A node is
 * buffered in memory before it is decoded.
 *
 * @since 5.2.21
 */
@Internal
public final class JsonStreamElementLimit {
    private static final String KEY = JsonStreamElementLimit.class.getName();

    private JsonStreamElementLimit() {
    }

    /**
     * Limit the size of every node of a publisher returned by a chunked JSON reader.
     *
     * @param publisher      The publisher returned by the reader
     * @param maxElementSize The maximum size of a single node
     * @param <T>            The element type
     * @return The limited publisher
     */
    public static <T> Flux<T> limit(Publisher<T> publisher, long maxElementSize) {
        return Flux.from(publisher).contextWrite(ctx -> ctx.put(KEY, maxElementSize));
    }

    static long get(ContextView context) {
        return context.<Long>getOrEmpty(KEY).orElse(Long.MAX_VALUE);
    }
}
