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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.body.RawDuplexHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * The {@code 101 Switching Protocols} response of the Netty client: the connection now carries
 * the new protocol through a {@link RawDuplexHandler}, whose inbound bytes are the body.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyClientUpgradedResponse extends NettyClientByteBodyResponse implements UpgradedHttpResponse<Object> {
    private final RawDuplexHandler duplex;

    NettyClientUpgradedResponse(HttpResponse nettyResponse, RawDuplexHandler duplex, ConversionService conversionService) {
        super(nettyResponse, duplex.inbound(), conversionService);
        this.duplex = duplex;
    }

    @Override
    public void send(CloseableByteBody outbound) {
        duplex.send(outbound);
    }

    @Override
    public @Nullable String getProtocol() {
        return nettyResponse.headers().get(HttpHeaderNames.UPGRADE);
    }

    @Override
    public void close() {
        super.close();
        duplex.close();
    }
}
