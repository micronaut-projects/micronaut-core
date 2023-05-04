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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;

/**
 * Netty-specific writer.
 *
 * @param <T> The type to write
 */
@Internal
@Experimental
public interface NettyBodyWriter<T> extends MessageBodyWriter<T> {

    /**
     * Write an object to the given context.
     *
     * @param request          The associated request
     * @param outgoingResponse The outgoing response.
     * @param type             The type
     * @param mediaType        The media type
     * @param object           The object to write
     * @param nettyContext     The netty context
     * @throws CodecException If an error occurs decoding
     */
    @NonNull
    void writeTo(
        @NonNull HttpRequest<?> request,
        @NonNull MutableHttpResponse<T> outgoingResponse,
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        @NonNull T object,
        @NonNull NettyWriteContext nettyContext) throws CodecException;
}
