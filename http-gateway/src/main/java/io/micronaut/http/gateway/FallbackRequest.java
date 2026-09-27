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
    private final CloseableByteBody body;

    @SuppressWarnings("unchecked")
    FallbackRequest(HttpRequest<?> request, CloseableByteBody body) {
        super((HttpRequest<Object>) request);
        this.body = body;
    }

    @Override
    public ByteBody byteBody() {
        return body;
    }

    @Override
    public @Nullable ByteBody byteBodyDirect() {
        return body;
    }

    @Override
    public ByteBodyFactory byteBodyFactory() {
        return getDelegate() instanceof ServerHttpRequest<?> server ? server.byteBodyFactory() : ServerHttpRequest.super.byteBodyFactory();
    }

    /**
     * Release the bytes if nobody took them.
     */
    void release() {
        body.close();
    }
}
