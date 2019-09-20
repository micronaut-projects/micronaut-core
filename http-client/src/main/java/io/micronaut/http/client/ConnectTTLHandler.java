package io.micronaut.http.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A handler that will close channels after they have reached their time-to-live, regardless of usage.
 *
 * channels that are in use will be closed when they are next
 * released to the underlying connection pool.
 */
public class ConnectTTLHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ConnectTTLHandler.class);
    private final Long connectionTtlMillis;
    public static final AttributeKey<Boolean> RELEASE_CHANNEL = AttributeKey.newInstance("release_channel");

    private ScheduledFuture<?> channelKiller;

    public ConnectTTLHandler(Long connectionTtlMillis) {
        if(connectionTtlMillis <=0){
            throw new IllegalArgumentException("connectTTL must be positive");
        }
        this.connectionTtlMillis = connectionTtlMillis;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        channelKiller = ctx.channel().eventLoop().schedule(()->closeChannel(ctx), connectionTtlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        channelKiller.cancel(false);
    }


    private void closeChannel(ChannelHandlerContext ctx) {
        if (ctx.channel().isOpen()) {
            log.trace( "Connection (" + ctx.channel().id() + ") will be closed during its next release, because it " +
                    "has reached its maximum time to live of " + connectionTtlMillis + " milliseconds.");
            ctx.channel().attr(RELEASE_CHANNEL).set(true);
        }
    }
}