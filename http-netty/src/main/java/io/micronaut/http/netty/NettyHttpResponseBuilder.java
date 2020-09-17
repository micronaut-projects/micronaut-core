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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.util.Objects;

/**
 * Common interface for client and server to implement to construct the Netty versions of the response objects.
 *
 * @author graemerocher
 * @since 2.0.0
 */
public interface NettyHttpResponseBuilder {

    /**
     * Converts this object to a full http response.
     *
     * @return a full http response
     */
    @NonNull FullHttpResponse toFullHttpResponse();

    /**
     * Converts this object to a streamed http response.
     * @return The streamed response
     */
    default @NonNull StreamedHttpResponse toStreamHttpResponse() {
        FullHttpResponse fullHttpResponse = toFullHttpResponse();
        DefaultStreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(
                fullHttpResponse.protocolVersion(),
                fullHttpResponse.status(),
                true,
                Publishers.just(new DefaultLastHttpContent(fullHttpResponse.content()))
        );
        streamedHttpResponse.headers().setAll(fullHttpResponse.headers());
        return streamedHttpResponse;
    }

    /**
     * Converts this object to the most appropriate http response type.
     * @return The http response
     */
    default @NonNull HttpResponse toHttpResponse() {
        if (isStream()) {
            return toStreamHttpResponse();
        } else {
            return toFullHttpResponse();
        }
    }

    /**
     * @return Is the response a stream.
     */
    boolean isStream();

    /**
     * Convert the given response to a full http response.
     * @param response The response
     * @return The full response.
     */
    static @NonNull HttpResponse toHttpResponse(@NonNull io.micronaut.http.HttpResponse<?> response) {
        Objects.requireNonNull(response, "The response cannot be null");
        while (response instanceof HttpResponseWrapper) {
            response = ((HttpResponseWrapper<?>) response).getDelegate();
        }
        if (response instanceof NettyHttpResponseBuilder) {
            return ((NettyHttpResponseBuilder) response).toHttpResponse();
        }
        // manual conversion
        HttpResponse fullHttpResponse;
        ByteBuf byteBuf = response.getBody(ByteBuf.class).orElse(null);
        if (byteBuf != null) {
            fullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(response.code(), response.reason()),
                    byteBuf
            );
        } else {
            fullHttpResponse = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(response.code(), response.reason())
            );
        }

        response.getHeaders()
                .forEach((s, strings) -> fullHttpResponse.headers().add(s, strings));
        return fullHttpResponse;
    }

    /**
     * Convert the given response to a full http response.
     * @param response The response
     * @return The full response.
     */
    static @NonNull StreamedHttpResponse toStreamResponse(@NonNull io.micronaut.http.HttpResponse<?> response) {
        Objects.requireNonNull(response, "The response cannot be null");
        while (response instanceof HttpResponseWrapper) {
            response = ((HttpResponseWrapper<?>) response).getDelegate();
        }
        if (response instanceof NettyHttpResponseBuilder) {
            NettyHttpResponseBuilder builder = (NettyHttpResponseBuilder) response;
            if (builder.isStream()) {
                return builder.toStreamHttpResponse();
            } else {
                FullHttpResponse fullHttpResponse = builder.toFullHttpResponse();
                return new DefaultStreamedHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(response.code(), response.reason()),
                        Publishers.just(new DefaultLastHttpContent(fullHttpResponse.content()))
                );
            }
        }
        // manual conversion
        StreamedHttpResponse fullHttpResponse;
        ByteBuf byteBuf = response.getBody(ByteBuf.class).orElse(null);
        if (byteBuf != null) {
            fullHttpResponse = new DefaultStreamedHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(response.code(), response.reason()),
                    Publishers.just(new DefaultLastHttpContent(byteBuf))
            );
        } else {
            fullHttpResponse = new DefaultStreamedHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(response.code(), response.reason()),
                    Publishers.empty()
            );
        }

        response.getHeaders()
                .forEach((s, strings) -> fullHttpResponse.headers().add(s, strings));
        return fullHttpResponse;
    }
}
