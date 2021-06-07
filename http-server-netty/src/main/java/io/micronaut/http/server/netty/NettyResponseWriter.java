package io.micronaut.http.server.netty;

import io.netty.channel.ChannelHandlerContext;

public interface NettyResponseWriter {

    void write(ChannelHandlerContext context, NettyHttpRequest<?> request);
}
