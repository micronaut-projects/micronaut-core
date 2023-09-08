/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.netty.buffer.ByteBufAllocator;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.function.Function;

/**
 * A body consisting of multiple objects of arbitrary type. Basically a
 * {@link Publisher}{@code <?>}. This class is so generic for compatibility reasons, it's the
 * result of processing a {@link ByteBody} using a
 * {@link io.micronaut.http.server.netty.HttpContentProcessor}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public sealed interface MultiObjectBody extends HttpBody permits ImmediateMultiObjectBody, ImmediateSingleObjectBody, StreamingMultiObjectBody {
    /**
     * Coerce this value to an {@link InputStream}. This implements
     * {@link io.micronaut.http.server.netty.binders.NettyInputStreamBodyBinder}. Requires the objects
     * of this body to be {@link io.netty.buffer.ByteBuf}s.<br>
     * Ownership is transferred to the stream, it must be closed to release all buffers.
     *
     * @param alloc The buffer allocator to use
     * @return The stream that reads the data in this body
     */
    InputStream coerceToInputStream(ByteBufAllocator alloc);

    /**
     * Get this value as a publisher. The publisher must be subscribed to exactly once. All objects
     * forwarded to the subscriber become its responsibility and must be released by the
     * subscriber.
     *
     * @return The publisher
     */
    Publisher<?> asPublisher();

    /**
     * Apply a mapping function to all objects in this body. {@code null} values in the output are
     * skipped.
     *
     * @param transform The mapping function
     * @return A new body with the mapped values
     */
    MultiObjectBody mapNotNull(Function<Object, Object> transform);

    /**
     * Special handling for form data. This method basically acts like
     * {@code asPublisher().subscribe(formRouteCompleter)}. However, {@link FormRouteCompleter}
     * needs to release the form data fields when the request is destroyed. To do this, it
     * implements {@link HttpBody#release()}. By calling this method, the
     * {@link FormRouteCompleter} is registered as the {@link #next() next body} and will be
     * released.
     *
     * @param formRouteCompleter The form route completer that should take over processing
     */
    void handleForm(FormRouteCompleter formRouteCompleter);
}
