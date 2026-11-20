/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.DirectByteBodyAccess;
import org.jspecify.annotations.Nullable;

/**
 * The request a fallback answers, with the bytes of the body kept for it, after an attempt
 * sent the body of the request: its {@link #byteBody()} is them, and a gateway route that is the
 * fallback, e.g. of a failover to another upstream, relays them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class FallbackRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, DirectByteBodyAccess {
    @Nullable
    private final CloseableByteBody body;
    @Nullable
    private final MutableHttpRequest<Object> prepared;
    private final long sizeLimit;

    FallbackRequest(HttpRequest<?> request, CloseableByteBody body) {
        this(request, body, null, -1);
    }

    /**
     * @param request   The inbound request
     * @param body      The bytes kept for the fallback, or {@code null} if the request still has them
     * @param prepared  The upstream request the route prepared with its filters, which a failover
     *                  relays as it is, or {@code null}
     * @param sizeLimit The limit of the body of the route, {@code -1} for none
     */
    @SuppressWarnings("unchecked")
    FallbackRequest(HttpRequest<?> request, @Nullable CloseableByteBody body, @Nullable MutableHttpRequest<Object> prepared, long sizeLimit) {
        super((HttpRequest<Object>) request);
        this.body = body;
        this.prepared = prepared;
        this.sizeLimit = sizeLimit;
    }

    /**
     * @return The upstream request of the route, with its filters applied, for a failover: its
     * credentials, path and headers are those the route sends, not those of the client
     */
    @Nullable MutableHttpRequest<Object> prepared() {
        return prepared;
    }

    /**
     * @return The limit of the body of the route that failed over, {@code -1} for none
     */
    long sizeLimit() {
        return sizeLimit;
    }

    @Override
    public ByteBody byteBody() {
        if (body != null) {
            return body;
        }
        if (getDelegate() instanceof ServerHttpRequest<?> server) {
            return server.byteBody();
        }
        throw new IllegalStateException("The request of the fallback has no body of a server request");
    }

    @Override
    public @Nullable ByteBody byteBodyDirect() {
        if (body != null) {
            return body;
        }
        return getDelegate() instanceof DirectByteBodyAccess direct ? direct.byteBodyDirect() : null;
    }

    @Override
    public @Nullable Runnable whenAbandoned(Runnable callback) {
        // the fallback stops when the client of the inbound request goes away
        return getDelegate() instanceof ServerHttpRequest<?> server ? server.whenAbandoned(callback) : null;
    }

    @Override
    public ByteBodyFactory byteBodyFactory() {
        return getDelegate() instanceof ServerHttpRequest<?> server ? server.byteBodyFactory() : ServerHttpRequest.super.byteBodyFactory();
    }

    /**
     * Release the bytes if nobody took them.
     */
    void release() {
        if (body != null) {
            body.close();
        }
    }
}
