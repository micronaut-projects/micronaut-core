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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * {@link NettyBodyWriter} extension that can do without a {@link HttpRequest}.
 *
 * @param <T> The body type
 * @since 4.3.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
public sealed interface ShortCircuitNettyBodyWriter<T> extends NettyBodyWriter<T> permits NettyJsonHandler, NettyTextPlainHandler {
    @Override
    default void writeTo(HttpRequest<?> request, MutableHttpResponse<T> outgoingResponse, Argument<T> type, MediaType mediaType, T object, NettyWriteContext writeContext) throws CodecException {
        MutableHttpHeaders headers = outgoingResponse.getHeaders();
        NettyHttpHeaders nettyHttpHeaders = (NettyHttpHeaders) headers;
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyHttpHeaders.getNettyHeaders();
        if (!nettyHttpHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            nettyHttpHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        writeTo(request.getHeaders(), HttpResponseStatus.valueOf(outgoingResponse.code(), outgoingResponse.reason()), nettyHeaders, object, writeContext);
    }

    /**
     * Write an object to the given context.
     *
     * @param requestHeaders  The request headers
     * @param status          The response status
     * @param responseHeaders The response headers
     * @param object          The object to write
     * @param nettyContext    The netty context
     * @throws CodecException If an error occurs decoding
     */
    void writeTo(
        HttpHeaders requestHeaders,
        HttpResponseStatus status,
        io.netty.handler.codec.http.HttpHeaders responseHeaders,
        T object,
        NettyWriteContext nettyContext
    );
}
