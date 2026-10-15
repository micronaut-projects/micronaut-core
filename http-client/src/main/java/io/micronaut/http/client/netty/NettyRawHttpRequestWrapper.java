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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.internal.RawHttpRequestWrapper;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.netty.handler.codec.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * The {@link RawHttpRequestWrapper} of the Netty client: a {@link NettyHttpRequestBuilder}, so
 * that the raw bytes are sent as they are.
 *
 * @param <B> The body type, mostly unused
 * @since 4.7.0
 */
@Internal
final class NettyRawHttpRequestWrapper<B> extends RawHttpRequestWrapper<B> implements NettyHttpRequestBuilder {

    NettyRawHttpRequestWrapper(ConversionService conversionService, MutableHttpRequest<B> delegate, CloseableByteBody byteBody) {
        super(conversionService, delegate, byteBody);
    }

    @Override
    public @Nullable ByteBody byteBodyDirect() {
        return isBodyReplaced() ? null : byteBody();
    }

    @Override
    public HttpRequest toHttpRequestWithoutBody() {
        return NettyHttpRequestBuilder.asBuilder(getDelegate()).toHttpRequestWithoutBody();
    }

    @Override
    public HttpRequest toHttpRequestWithoutBody(String requestTarget) {
        return NettyHttpRequestBuilder.asBuilder(getDelegate()).toHttpRequestWithoutBody(requestTarget);
    }
}
