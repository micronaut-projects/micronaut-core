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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;

/**
 * Releases a buffer of a server or client runtime that a Reactor input of a chunked reader
 * discards, e.g. a Netty buffer it held before it was mapped to a piece: the foreign discard of
 * {@link PieceReaders#publisherOfBuffers(org.reactivestreams.Publisher, io.micronaut.http.body.PieceReader, java.util.function.Function, java.util.function.Consumer)}.
 * A runtime whose buffers are neither a {@link ReadBuffer} nor a Micronaut
 * {@link ReferenceCounted} provides it as a bean, and the readers that do not depend on the
 * runtime, e.g. the JSON readers, receive it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@FunctionalInterface
public interface ForeignBufferReleaser {

    /**
     * Release the object if it is a buffer of the runtime that is not released yet, else do
     * nothing.
     *
     * @param object The discarded object
     */
    void release(Object object);
}
