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
package io.micronaut.http.netty;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.util.Objects;

/**
 * Common interface for client and server to implement to construct the Netty versions of the request objects.
 *
 * @author graemerocher
 * @since 2.0.0
 */
public interface NettyHttpRequestBuilder {
    /**
     * Converts this object to a full http request.
     *
     * @return a full http request
     */
    @NonNull
    FullHttpRequest toFullHttpRequest();

    /**
     * Converts this object to a streamed http request.
     * @return The streamed request
     */
    @NonNull
    StreamedHttpRequest toStreamHttpRequest();

    /**
     * Converts this object to the most appropriate http request type.
     * @return The http request
     */
    @NonNull
    HttpRequest toHttpRequest();

    /**
     * @return Is the request a stream.
     */
    boolean isStream();

    /**
     * Convert the given request to a full http request.
     * @param request The request
     * @return The full request.
     */
    static @NonNull HttpRequest toHttpRequest(@NonNull io.micronaut.http.HttpRequest<?> request) {
        Objects.requireNonNull(request, "The request cannot be null");
        while (request instanceof HttpRequestWrapper) {
            request = ((HttpRequestWrapper<?>) request).getDelegate();
        }
        if (request instanceof NettyHttpRequestBuilder) {
            return ((NettyHttpRequestBuilder) request).toHttpRequest();
        }
        // manual conversion
        HttpRequest nettyRequest;
        ByteBuf byteBuf = request.getBody(ByteBuf.class).orElse(null);
        if (byteBuf != null) {
            nettyRequest = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.valueOf(request.getMethodName()),
                    request.getUri().toString(),
                    byteBuf
            );
        } else {
            nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.valueOf(request.getMethodName()),
                    request.getUri().toString()
            );
        }

        request.getHeaders()
                .forEach((s, strings) -> nettyRequest.headers().add(s, strings));
        return nettyRequest;
    }

}
