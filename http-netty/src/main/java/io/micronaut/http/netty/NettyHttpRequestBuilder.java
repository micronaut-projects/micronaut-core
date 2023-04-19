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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Optional;

/**
 * Common interface for client and server to implement to construct the Netty versions of the request objects.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@Internal
public interface NettyHttpRequestBuilder {
    /**
     * Converts this object to a full http request.
     *
     * @return a full http request
     */
    @Deprecated
    @NonNull
    default FullHttpRequest toFullHttpRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Converts this object to a streamed http request.
     *
     * @return The streamed request
     */
    @Deprecated
    @NonNull
    default StreamedHttpRequest toStreamHttpRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Converts this object to the most appropriate http request type.
     * @return The http request
     */
    @NonNull
    @Deprecated
    default HttpRequest toHttpRequest() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    default Optional<HttpRequest> toHttpRequestDirect() {
        return Optional.empty();
    }

    @NonNull
    HttpRequest toHttpRequestWithoutBody();

    /**
     * @return Is the request a stream.
     */
    @Deprecated
    default boolean isStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Convert the given request to a full http request.
     * @param request The request
     * @return The full request.
     */
    @Deprecated
    static @NonNull HttpRequest toHttpRequest(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return asBuilder(request).toHttpRequestWithoutBody();
    }

    static NettyHttpRequestBuilder asBuilder(@NonNull io.micronaut.http.HttpRequest<?> request) {
        boolean supportDirect = true;
        while (request instanceof HttpRequestWrapper<?> wrapper) {
            supportDirect &= wrapper.getBody() == wrapper.getDelegate().getBody();
            request = wrapper.getDelegate();
        }
        if (request instanceof NettyHttpRequestBuilder builder) {
            if (supportDirect) {
                return builder;
            } else {
                // delegate to builder, excluding toHttpRequestDirect
                //noinspection Convert2Lambda
                return new NettyHttpRequestBuilder() {
                    @Override
                    public HttpRequest toHttpRequestWithoutBody() {
                        return builder.toHttpRequestWithoutBody();
                    }
                };
            }
        }

        // manual conversion
        HttpRequest nettyRequest = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.valueOf(request.getMethodName()),
            request.getUri().toString()
        );
        request.getHeaders()
            .forEach((s, strings) -> nettyRequest.headers().add(s, strings));
        return () -> nettyRequest;
    }

}
