package io.micronaut.http.client.netty;

import org.jspecify.annotations.NonNull;
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
    /**
     * Shared between all pools, so that a pool that is created after an unused pool has been
     * evicted continues with the next event loop (i.e. the next embedded channel). Tests may
     * reset it to make the next new connection land on the event loop of the next channel.
     */
    final AtomicInteger preferredPoolCounter = new AtomicInteger();

    EmbeddedConnectionManager(ConnectionManager from, List<EmbeddedChannel> channels, List<ChannelFuture> openFutures) {
        super(from);
        this.channels = channels;
        this.openFutures = openFutures;
    }

    @Override
    ChannelFuture doConnect(NettyHttpClient.RequestKey requestKey, CustomizerAwareInitializer channelInitializer, @NonNull EventLoopGroup eventLoop) {
        try {
            channelInitializer.bootstrappedCustomizer = clientCustomizer;
            int index = i++;
            var connection = channels.get(index);
            return openFutures.get(index)
                .addListener(future -> {
                    // like a real failed connect, a failed open never initializes the channel
                    if (future.isSuccess()) {
                        connection.pipeline().addLast(channelInitializer);
                    }
                });
        } catch (Throwable t) {
            // print it immediately to make sure it's not swallowed
            t.printStackTrace();
            throw t;
        }
    }

    @Override
    PoolHolder createPool(NettyHttpClient.RequestKey requestKey, Iterable<? extends EventExecutor> group) {
        PoolHolder pool = super.createPool(requestKey, channels.stream().map(EmbeddedChannel::eventLoop).toList());
        ((Pool49) pool.pool).pickPreferredPoolOverride = l -> l.get(preferredPoolCounter.getAndIncrement() % l.size());
        return pool;
    }
}
