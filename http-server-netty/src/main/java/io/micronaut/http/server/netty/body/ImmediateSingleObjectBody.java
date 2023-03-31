/*
 * Copyright 2017-2023 original authors
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
