package org.particleframework.http.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.server.HttpServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Responsible for relaying the response to the client
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponseTransmitter {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final Charset defaultCharset;
    private final ConversionService conversionService;

    public NettyHttpResponseTransmitter(HttpServerConfiguration serverConfiguration, ConversionService conversionService) {
        this.defaultCharset = serverConfiguration.getDefaultCharset();
        this.conversionService = conversionService;
    }


}
