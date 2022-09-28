package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

/**
 * Handler for connection failures that happen during the handshake phases of a connection.
 */
@Internal
@ChannelHandler.Sharable
abstract class InitialConnectionErrorHandler extends ChannelInboundHandlerAdapter {
    private static final AttributeKey<Throwable> FAILURE_KEY =
        AttributeKey.valueOf(InitialConnectionErrorHandler.class, "FAILURE_KEY");

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        setFailureCause(ctx.channel(), cause);
        ctx.close();
    }

    static void setFailureCause(Channel channel, Throwable cause) {
        channel.attr(FAILURE_KEY).set(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        onNewConnectionFailure(ctx.channel().attr(FAILURE_KEY).get());
    }

    protected abstract void onNewConnectionFailure(@Nullable Throwable cause) throws Exception;
}
