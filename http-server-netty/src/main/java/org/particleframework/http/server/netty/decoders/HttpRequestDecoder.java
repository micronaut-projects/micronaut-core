/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty.decoders;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpRequest;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.NettyHttpRequest;

import java.util.List;

/**
 * A {@link MessageToMessageDecoder} that decodes a Netty {@link HttpRequest} into a Particle {@link org.particleframework.http.HttpRequest}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
public class HttpRequestDecoder extends MessageToMessageDecoder<HttpRequest> implements Ordered {

    public static final String ID = "particle-http-decoder";

    private final ConversionService<?> conversionService;
    private final HttpServerConfiguration configuration;

    public HttpRequestDecoder(ConversionService<?> conversionService, HttpServerConfiguration configuration) {
        this.conversionService = conversionService;
        this.configuration = configuration;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, List<Object> out) throws Exception {
        out.add(new NettyHttpRequest<>(msg, ctx, conversionService, configuration));
    }
}
