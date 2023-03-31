package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.util.ReferenceCountUtil;

/**
 * {@link HttpBody} that contains a single object. This is used to implement
 * {@link NettyHttpRequest#getBody()} and {@link java.util.concurrent.CompletableFuture} binding.
 */
@Internal
public final class ImmediateSingleObjectBody extends ManagedBody<Object> implements HttpBody {
    ImmediateSingleObjectBody(Object value) {
        super(value);
    }

    @Override
    void release(Object value) {
        ReferenceCountUtil.release(value);
    }

    /**
     * Get the value and transfer ownership to the caller. The caller must release the value after
     * it's done. Can only be called once.
     *
     * @return The claimed value
     */
    public Object claimForExternal() {
        return claim();
    }

    /**
     * Get the value without transferring ownership. The returned value may become invalid when
     * other code calls {@link #claimForExternal()} or when the netty request is destroyed.
     *
     * @return The unclaimed value
     */
    public Object valueUnclaimed() {
        return value();
    }
}
