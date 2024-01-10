package io.micronaut.http.server.netty.shortcircuit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;

@Internal
public interface PreparedHandler {
    void accept(NettyHttpRequest<Object> nhr, PipeliningServerHandler.OutboundAccess outboundAccess);
}
