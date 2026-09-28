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
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.DirectByteBodyAccess;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The copy of a request that a mirror relays: the request with a copy of its body, and
 * attributes of its own, so that the relay of the mirror does not change the ones of the
 * request.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class MirrorRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, DirectByteBodyAccess {
    private final CloseableByteBody body;
    private final MutableConvertibleValues<Object> attributes;

    @SuppressWarnings("unchecked")
    MirrorRequest(HttpRequest<?> request, CloseableByteBody body) {
        super((HttpRequest<Object>) request);
        this.body = body;
        Map<CharSequence, Object> copy = new LinkedHashMap<>();
        request.getAttributes().forEach((name, value) -> copy.put(name, value));
        this.attributes = new MutableConvertibleValuesMap<>(copy);
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public HttpRequest<Object> setAttribute(CharSequence name, @Nullable Object value) {
        if (value == null) {
            attributes.remove(name.toString());
        } else {
            attributes.put(name.toString(), value);
        }
        return this;
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
     * Release the copy of the body if the relay did not take it.
     */
    void release() {
        body.close();
    }
}
