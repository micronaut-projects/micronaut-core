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
package io.micronaut.http.server.netty.decoders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A {@link MessageToMessageDecoder} that decodes a Netty {@link HttpRequest} into a Micronaut
 * {@link io.micronaut.http.HttpRequest}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
@Internal
public class HttpRequestDecoder extends MessageToMessageDecoder<HttpRequest> implements Ordered {

    /**
     * Constant for Micronaut http decoder.
     */
    public static final String ID = "micronaut-http-decoder";

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final EmbeddedServer embeddedServer;
    private final ConversionService<?> conversionService;
    private final HttpServerConfiguration configuration;

    /**
     * @param embeddedServer    The embedded service
     * @param conversionService The conversion service
     * @param configuration     The Http server configuration
     */
    public HttpRequestDecoder(EmbeddedServer embeddedServer, ConversionService<?> conversionService, HttpServerConfiguration configuration) {
        this.embeddedServer = embeddedServer;
        this.conversionService = conversionService;
        this.configuration = configuration;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, List<Object> out) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Server {}:{} Received Request: {} {}", embeddedServer.getHost(), embeddedServer.getPort(), msg.method(), msg.uri());
        }
        try {
            NettyHttpRequest<Object> request = new NettyHttpRequest<>(msg, ctx, conversionService, configuration);
            ctx.executor().execute(() -> {
                try {
                    embeddedServer.getApplicationContext().publishEvent(
                            new HttpRequestReceivedEvent(request)
                    );
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing Http request received event: " + e.getMessage(), e);
                    }
                }
            });
            out.add(request);
        } catch (IllegalArgumentException e) {
            // this configured the request in the channel as an attribute
            new NettyHttpRequest<>(
                    new DefaultHttpRequest(msg.protocolVersion(), msg.method(), "/"),
                    ctx,
                    conversionService,
                    configuration
            );
            final Throwable cause = e.getCause();
            ctx.fireExceptionCaught(cause != null ? cause : e);
        }
    }
}
