package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.NonNull;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class EmbeddedConnectionManager extends ConnectionManager {
    final List<EmbeddedChannel> channels;
    final List<ChannelFuture> openFutures;

    private int i;

    EmbeddedConnectionManager(ConnectionManager from, List<EmbeddedChannel> channels, List<ChannelFuture> openFutures) {
        super(from);
        this.channels = channels;
        this.openFutures = openFutures;
    }

    @Override
    ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, CustomizerAwareInitializer channelInitializer, @NonNull EventLoopGroup eventLoop) {
        try {
            channelInitializer.bootstrappedCustomizer = clientCustomizer;
            int index = i++;
            var connection = channels.get(index);
            return openFutures.get(index)
                .addListener(future -> connection.pipeline().addLast(channelInitializer));
        } catch (Throwable t) {
            // print it immediately to make sure it's not swallowed
            t.printStackTrace();
            throw t;
        }
    }

    @Override
    PoolHolder createPool(DefaultHttpClient.RequestKey requestKey, Iterable<? extends EventExecutor> group) {
        PoolHolder pool = super.createPool(requestKey, channels.stream().map(EmbeddedChannel::eventLoop).toList());
        AtomicInteger j = new AtomicInteger();
        ((Pool49) pool.pool).pickPreferredPoolOverride = l -> l.get((j.getAndIncrement()) % l.size());
        return pool;
    }
}
