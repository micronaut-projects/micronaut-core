package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;

class BaseRouteCompleter {
    final RoutingInBoundHandler rib;
    final NettyHttpRequest<?> request;
    volatile boolean needsInput = true;
    /**
     * Optional runnable that may be called from other threads (i.e. downstream subscribers) to
     * notify that {@link #needsInput} may have changed.
     */
    @Nullable
    volatile Runnable checkDemand;
    RouteMatch<?> routeMatch;
    boolean execute = false;

    public BaseRouteCompleter(RoutingInBoundHandler rib, NettyHttpRequest<?> request, RouteMatch<?> routeMatch) {
        this.rib = rib;
        this.request = request;
        this.routeMatch = routeMatch;
    }

    final void add(Object message) throws Throwable {
        try {
            if (request.destroyed) {
                // we don't want this message anymore
                return;
            }

            if (message instanceof ByteBufHolder bbh) {
                addHolder(bbh);
            } else {
                ((NettyHttpRequest) request).setBody(message);
                needsInput = true;
            }

            // now, a pseudo try-finally with addSuppressed.
        } catch (Throwable t) {
            try {
                ReferenceCountUtil.release(message);
            } catch (Throwable u) {
                t.addSuppressed(u);
            }
            throw t;
        }

        // the upstream processor gives us ownership of the message, so we need to release it.
        ReferenceCountUtil.release(message);
    }

    void addHolder(ByteBufHolder holder) {
        request.addContent(holder);
        needsInput = true;
    }

    void completeSuccess() {
        execute = true;
    }

    void completeFailure(Throwable failure) {
        if (!execute) {
            // discard parameters that have already been bound
            for (Object toDiscard : routeMatch.getVariableValues().values()) {
                if (toDiscard instanceof ReferenceCounted) {
                    ((ReferenceCounted) toDiscard).release();
                }
                if (toDiscard instanceof io.netty.util.ReferenceCounted) {
                    ((io.netty.util.ReferenceCounted) toDiscard).release();
                }
                if (toDiscard instanceof NettyCompletedFileUpload) {
                    ((NettyCompletedFileUpload) toDiscard).discard();
                }
            }
        }
    }
}
