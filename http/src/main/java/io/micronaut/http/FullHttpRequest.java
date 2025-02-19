/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;

/**
 * Allows introspecting whether the request is a full http request.
 *
 * @param <B> The body type
 * @author James Kleeh
 * @since 1.1.0
 */
@Internal
@Deprecated
public interface FullHttpRequest<B> extends HttpRequest<B> {
    /**
     * Shortcut for {@code contents() != null}.
     *
     * @return Is the request full.
     */
    default boolean isFull() {
        return false;
    }

    /**
     * Get the raw body of this request. May be called multiple times. Buffer ownership is not
     * transferred to the caller.
     *
     * @return The body contents or null if there are none, or they are not obtainable.
     */
    @Nullable
    ByteBuffer<?> contents();

    /**
     * Get the contents of this request as a buffer. If this is a streaming request, the returned
     * flow may be delayed. If buffering is not supported for this request, this may return
     * {@code null}. Once the returned flow completes, {@link #contents()} must return the same
     * value.
     *
     * @return The request content, or {@code null} if buffering is not supported
     */
    @Nullable
    ExecutionFlow<ByteBuffer<?>> bufferContents();
}
