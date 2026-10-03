/*
 * Copyright 2017-2025 original authors
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * {@link IdleStateHandler} variant for the server pipelines. When a writer or all-idle timeout is
 * configured, the netty handler replaces every void write promise with a real one and adds a
 * listener to it in order to record the completion time of the write. The server writes almost
 * exclusively through void promises, so this costs a promise, a listener list and a listener
 * notification for every write. Since the server itself drives all writes, the last write time
 * is instead recorded when the write enters the handler, and the promise is passed through
 * untouched. The same {@link io.netty.handler.timeout.IdleStateEvent}s fire as with the netty
 * handler; the only difference is that a write counts as activity when it is issued, not when it
 * has been flushed to the transport.
 *
 * @since 5.3.0
 */
@Internal
final class ServerIdleStateHandler extends IdleStateHandler {
    ServerIdleStateHandler(long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit) {
        super(readerIdleTime, writerIdleTime, allIdleTime, unit);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        resetWriteTimeout();
        ctx.write(msg, promise);
    }
}
