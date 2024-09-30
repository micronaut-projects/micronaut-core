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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link ByteBodyHttpResponse} implementation for the client.
 *
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Internal
final class NettyClientByteBodyResponse implements ByteBodyHttpResponse<Object> {
    final HttpResponse nettyResponse;

    private final CloseableByteBody body;
    private final NettyHttpHeaders headers;
    private final Supplier<MutableConvertibleValues<Object>> attributes = SupplierUtil.memoized(MutableConvertibleValuesMap::new);

    NettyClientByteBodyResponse(HttpResponse nettyResponse, CloseableByteBody body, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.body = body;
        this.headers = new NettyHttpHeaders(nettyResponse.headers(), conversionService);
    }

    @Override
    public @NonNull ByteBody byteBody() {
        return body;
    }

    @Override
    public void close() {
        body.close();
    }

    @Override
    public int code() {
        return nettyResponse.status().code();
    }

    @Override
    public String reason() {
        return nettyResponse.status().reasonPhrase();
    }

    @Override
    public @NonNull NettyHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public @NonNull MutableConvertibleValues<Object> getAttributes() {
        return attributes.get();
    }

    @Override
    public @NonNull Optional<Object> getBody() {
        return Optional.empty();
    }
}
