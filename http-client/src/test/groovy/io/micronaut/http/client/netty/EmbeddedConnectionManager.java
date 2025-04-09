package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.List;

final class EmbeddedConnectionManager extends ConnectionManager {
    final List<EmbeddedChannel> channels;

    private int i;

    EmbeddedConnectionManager(ConnectionManager from, List<EmbeddedChannel> channels) {
        super(from);
        this.channels = channels;
    }

    @Override
    ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, CustomizerAwareInitializer channelInitializer, @NonNull EventLoopGroup eventLoop) {
        try {
            channelInitializer.bootstrappedCustomizer = clientCustomizer;
            var connection = channels.get(i++);
            connection.pipeline().addLast(channelInitializer);
            var promise = connection.newPromise();
            promise.setSuccess();
            return promise;
        } catch (Throwable t) {
            // print it immediately to make sure it's not swallowed
            t.printStackTrace();
            throw t;
        }
    }

    @Override
    Pool createPool(DefaultHttpClient.RequestKey requestKey) {
        return new Pool(requestKey, channels.stream().map(EmbeddedChannel::eventLoop).toList()) {
            int j = 0;

            @Override
            @Nullable
            LocalPoolPair pickPreferredPool() {
                return localPools.get((j++) % localPools.size());
            }
        };
    }
}
