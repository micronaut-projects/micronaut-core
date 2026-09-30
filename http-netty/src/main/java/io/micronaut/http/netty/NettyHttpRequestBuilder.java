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
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.DirectByteBodyAccess;
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
public interface NettyHttpRequestBuilder extends DirectByteBodyAccess {
    /**
     * Converts this object to a full http request.
     *
     * @return a full http request
     */
    @Deprecated
    default FullHttpRequest toFullHttpRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Converts this object to a streamed http request.
     *
     * @return The streamed request
     * @deprecated Go through {@link #toHttpRequestDirect()} and {@link #toHttpRequestWithoutBody()} instead
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    default StreamedHttpRequest toStreamHttpRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Converts this object to the most appropriate http request type.
     * @return The http request
     * @deprecated Go through {@link #toHttpRequestDirect()} and {@link #toHttpRequestWithoutBody()} instead
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    default HttpRequest toHttpRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Directly convert this request to netty, including the body, if possible. If the body of this
     * request has been changed, this will return an empty value.
     *
     * @return The request including the body
     * @deprecated Go through {@link #toHttpRequestWithoutBody()} and {@link #byteBodyDirect()} instead
     */
    @Deprecated
    default Optional<HttpRequest> toHttpRequestDirect() {
        return Optional.empty();
    }

    /**
     * Directly convert this request body to a {@link ByteBody}, if possible. If the body of this
     * request has been changed, this will return an empty value.
     *
     * @return The body
     */
    @Override
    @Nullable
    default ByteBody byteBodyDirect() {
        return null;
    }

    /**
     * Convert this request to a netty request without the body. The caller will handle adding the
     * body.
     *
     * @return The request excluding the body
     */
    HttpRequest toHttpRequestWithoutBody();

    /**
     * Convert this request to a netty request without the body, with the given request target
     * (path and query) instead of the URI of this request. The caller will handle adding the
     * body.
     *
     * @param requestTarget The request target to use as the netty request URI
     * @return The request excluding the body
     * @since 5.3.0
     */
    default HttpRequest toHttpRequestWithoutBody(String requestTarget) {
        // do not change the URI of the returned request, it may be the backing request of this one
        HttpRequest request = toHttpRequestWithoutBody();
        return new DefaultHttpRequest(request.protocolVersion(), request.method(), requestTarget, request.headers());
    }

    /**
     * @return Is the request a stream.
     * @deprecated Go through {@link #toHttpRequestDirect()} and {@link #toHttpRequestWithoutBody()} instead
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    default boolean isStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Convert the given request to a full http request.
     * @param request The request
     * @return The full request.
     * @deprecated Go through {@link #toHttpRequestDirect()} and {@link #toHttpRequestWithoutBody()} instead
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    static HttpRequest toHttpRequest(io.micronaut.http.HttpRequest<?> request) {
        return asBuilder(request).toHttpRequestWithoutBody();
    }

    /**
     * Transform the given request to an equivalent {@link NettyHttpRequestBuilder}, so that it can
     * be transformed to a netty request.
     *
     * @param request The micronaut http request
     * @return The builder for further operations
     */
    static NettyHttpRequestBuilder asBuilder(io.micronaut.http.HttpRequest<?> request) {
        boolean supportDirect = true;

        while (true) {
            if (request instanceof NettyHttpRequestBuilder builder) {
                if (supportDirect) {
                    return builder;
                } else {
                    // don't allow direct access to body
                    return builder::toHttpRequestWithoutBody;
                }
            }
            if (!(request instanceof HttpRequestWrapper<?> wrapper)) {
                break;
            }

            supportDirect &= wrapper.getBody().equals(wrapper.getDelegate().getBody());
            request = wrapper.getDelegate();
        }

        // manual conversion. This is done lazily, so that the builder reflects header changes
        // made after asBuilder was called
        io.micronaut.http.HttpRequest<?> finalRequest = request;
        return () -> {
            HttpRequest nettyRequest = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(finalRequest.getMethodName()),
                finalRequest.getUri().toString()
            );
            finalRequest.getHeaders()
                .forEach((s, strings) -> nettyRequest.headers().add(s, strings));
            return nettyRequest;
        };
    }
}
