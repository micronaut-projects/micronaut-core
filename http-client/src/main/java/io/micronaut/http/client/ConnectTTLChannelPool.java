package io.micronaut.http.client;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class ConnectTTLChannelPool implements ChannelPool {

    private final ChannelPool delegatePool;

    public ConnectTTLChannelPool(ChannelPool delegatePool) {
        this.delegatePool = delegatePool;
    }

    @Override
    public Future<Channel> acquire() {
        return delegatePool.acquire();
    }

    @Override
    public Future<Channel> acquire(Promise<Channel> promise) {
        return delegatePool.acquire(promise);
    }

    @Override
    public Future<Void> release(Channel channel) {
        return release(channel, channel.eventLoop().newPromise());
    }

    @Override
    public Future<Void> release(Channel channel, Promise<Void> promise) {

        doInEventLoop(channel.eventLoop(), () -> {
            boolean shouldCloseOnRelease = Boolean.TRUE.equals(channel.attr(AttributeKey.valueOf("realse_channel")).get());

            if (shouldCloseOnRelease && channel.isOpen() && !channel.eventLoop().isShuttingDown()) {
                channel.close();
            }

            delegatePool.release(channel, promise);
        });
        return promise;
    }

    private void doInEventLoop(EventExecutor eventExecutor, Runnable runnable) {
        if (eventExecutor.inEventLoop()) {
            runnable.run();
        } else {
            eventExecutor.submit(runnable);
        }
    }
    @Override
    public void close() {
        delegatePool.close();
    }
}
