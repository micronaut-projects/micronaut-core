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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;

/**
 * Extension to {@link MessageBodyWriter} that is specific to writing the server response body. This
 * allows more fine-grained control over the response than the {@link MessageBodyWriter} API.
 *
 * @param <T> The body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
@Indexed(MessageBodyWriter.class)
public interface ResponseBodyWriter<T> extends MessageBodyWriter<T> {
    /**
     * Writes an object as a {@link ByteBodyHttpResponse}.
     *
     * @param bufferFactory The buffer factory
     * @param request       The request
     * @param httpResponse  The response
     * @param type          The response body type
     * @param mediaType     The media type
     * @param object        The object to write
     * @return A {@link ByteBodyHttpResponse} with the response bytes
     * @throws CodecException If an error occurs encoding
     */
    @NonNull
    ByteBodyHttpResponse<?> write(
        @NonNull ByteBufferFactory<?, ?> bufferFactory,
        @NonNull HttpRequest<?> request,
        @NonNull MutableHttpResponse<T> httpResponse,
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        T object) throws CodecException;

    /**
     * Wrap the given writer, if necessary, to get a {@link ResponseBodyWriter}.
     *
     * @param writer The generic message writer
     * @return The response writer
     * @param <T> The body type
     */
    @NonNull
    static <T> ResponseBodyWriter<T> wrap(@NonNull MessageBodyWriter<T> writer) {
        if (writer instanceof ResponseBodyWriter<T> rbw) {
            return rbw;
        } else {
            return new ResponseBodyWriterWrapper<>(writer);
        }
    }
}
