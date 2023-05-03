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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.Toggleable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

import java.util.Collection;

/**
 * This class represents the first step of the HTTP body parsing pipeline. It transforms
 * {@link ByteBufHolder} instances that come from a
 * {@link io.micronaut.http.netty.stream.StreamedHttpRequest} into parsed objects, e.g. json nodes
 * or form data fragments.<br>
 * Processors are stateful. They can receive repeated calls to {@link #add} with more data,
 * followed by a call to {@link #complete} to finish up. Both of these methods accept a
 * {@link Collection} {@code out} parameter that is populated with the processed items.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Use the {@link io.micronaut.http.body.MessageBodyReader} API instead
 */
@Deprecated
public interface HttpContentProcessor extends Toggleable {
    /**
     * Process more data.
     *
     * @param data The input data
     * @param out The collection to add output items to
     */
    void add(ByteBufHolder data, Collection<Object> out) throws Throwable;

    /**
     * Finish processing data.
     *
     * @param out The collection to add remaining output items to
     */
    default void complete(Collection<Object> out) throws Throwable {
    }

    /**
     * Cancel processing, clean up any data. After this, there should be no more calls to
     * {@link #add} and {@link #complete}.
     */
    default void cancel() throws Throwable {
    }

    /**
     * Set the type of the values returned by this processor. Most processors do not respect this
     * setting, but e.g. the {@link io.micronaut.http.server.netty.jackson.JsonContentProcessor}
     * does.
     *
     * @param type The type produced by this processor
     * @return This processor, for chaining
     */
    default HttpContentProcessor resultType(Argument<?> type) {
        return this;
    }

    /**
     * Process a single {@link ByteBuf} into a single item, if possible.
     *
     * @param data The input data
     * @return The output value, or {@code null} if this is unsupported.
     * @throws Throwable Any failure
     */
    @Nullable
    default Object processSingle(ByteBuf data) throws Throwable {
        return null;
    }
}
