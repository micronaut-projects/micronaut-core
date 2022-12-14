/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;

/**
 * This class consumes objects produced by a {@link HttpContentProcessor}. Normally it just adds
 * the data to the {@link NettyHttpRequest}. For multipart data, there is additional logic in
 * {@link FormRouteCompleter} that also dynamically binds parameters, though usually this is done
 * by the {@link io.micronaut.http.server.binding.RequestArgumentSatisfier}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
class BaseRouteCompleter {
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

    public BaseRouteCompleter(NettyHttpRequest<?> request, RouteMatch<?> routeMatch) {
        this.request = request;
        this.routeMatch = routeMatch;
    }

    final void add(Object message) throws Throwable {
        try {
            if (request.destroyed) {
                // we don't want this message anymore
                ReferenceCountUtil.release(message);
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

    protected void addHolder(ByteBufHolder holder) {
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
                if (toDiscard instanceof ReferenceCounted rc) {
                    rc.release();
                }
                if (toDiscard instanceof io.netty.util.ReferenceCounted rc) {
                    rc.release();
                }
                if (toDiscard instanceof NettyCompletedFileUpload fu) {
                    fu.discard();
                }
            }
        }
    }
}
