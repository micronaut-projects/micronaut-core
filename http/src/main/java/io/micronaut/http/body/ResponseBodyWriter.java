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
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;

/**
 * Extension to {@link MessageBodyWriter} that is specific to writing the server response body. This
 * allows more fine-grained control over the response than the {@link MessageBodyWriter} API.
 *
 * @param <T> The body type
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Experimental
@Indexed(MessageBodyWriter.class)
public interface ResponseBodyWriter<T> extends MessageBodyWriter<T> {
    /**
     * Writes an object as a {@link ByteBodyHttpResponse}.
     *
     * @param bodyFactory  The buffer factory
     * @param request      The request
     * @param httpResponse The response
     * @param type         The response body type
     * @param mediaType    The media type
     * @param object       The object to write
     * @return A {@link ByteBodyHttpResponse} with the response bytes
     * @throws CodecException If an error occurs encoding
     */
    default ByteBodyHttpResponse<?> write(
        ByteBodyFactory bodyFactory,
        HttpRequest<?> request,
        MutableHttpResponse<T> httpResponse,
        Argument<T> type,
        MediaType mediaType,
        T object) throws CodecException {
        httpResponse.getHeaders().contentTypeIfMissing(mediaType);
        return ByteBodyHttpResponseWrapper.wrap(httpResponse, writePiece(bodyFactory, request, httpResponse, type, mediaType, object));
    }

    /**
     * Write a <i>piece</i> of a larger response, e.g. when writing a Publisher or a part of a
     * multipart response. In this case, response headers cannot be modified.
     *
     * @param bodyFactory The buffer factory
     * @param request     The request
     * @param response    The response this piece is part of
     * @param type        The type of this piece
     * @param mediaType   The media type of this piece
     * @param object      The piece to write
     * @return The response bytes
     * @throws CodecException If an error occurs encoding
     */
    CloseableByteBody writePiece(
        ByteBodyFactory bodyFactory,
        HttpRequest<?> request,
        HttpResponse<?> response,
        Argument<T> type,
        MediaType mediaType,
        T object) throws CodecException;

    /**
     * Wrap the given writer, if necessary, to get a {@link ResponseBodyWriter}.
     *
     * @param writer The generic message writer
     * @param <T>    The body type
     * @return The response writer
     */
    static <T> ResponseBodyWriter<T> wrap(MessageBodyWriter<T> writer) {
        if (writer instanceof ResponseBodyWriter<T> rbw) {
            return rbw;
        } else {
            return new ResponseBodyWriterWrapper<>(writer);
        }
    }
}
