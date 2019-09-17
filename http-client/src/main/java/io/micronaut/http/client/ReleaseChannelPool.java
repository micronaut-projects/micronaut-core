package io.micronaut.http.client;


import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;
import java.util.concurrent.atomic.AtomicBoolean;


public class ReleaseChannelPool implements ChannelPool {

    private static final AttributeKey<AtomicBoolean> IS_RELEASED = AttributeKey.newInstance("isReleased");

    private final ChannelPool delegate;

    public ReleaseChannelPool(ChannelPool delegate) {
        this.delegate = delegate;
    }

    @Override
    public Future<Channel> acquire() {
        return delegate.acquire().addListener(onAcquire());
    }

    @Override
    public Future<Channel> acquire(Promise<Channel> promise) {
        return delegate.acquire(promise).addListener(onAcquire());
    }

    private GenericFutureListener<Future<Channel>> onAcquire() {
        return future -> {
            if (future.isSuccess()) {
                future.getNow().attr(IS_RELEASED).set(new AtomicBoolean(false));
            }
        };
    }

    @Override
    public Future<Void> release(Channel channel) {
        if (shouldRelease(channel)) {
            return delegate.release(channel);
        } else {
            return new SucceededFuture<>(channel.eventLoop(), null);
        }
    }

    @Override
    public Future<Void> release(Channel channel, Promise<Void> promise) {
        if (shouldRelease(channel)) {
            return delegate.release(channel, promise);
        } else {
            return promise.setSuccess(null);
        }
    }

    private boolean shouldRelease(Channel channel) {
        // IS_RELEASED may be null if this channel was not acquired by this pool. This can happen
        // for HTTP/2 when we release the parent socket channel
        return channel.attr(IS_RELEASED).get() == null
                || channel.attr(IS_RELEASED).get().compareAndSet(false, true);
    }

    @Override
    public void close() {
        delegate.close();
    }
}

