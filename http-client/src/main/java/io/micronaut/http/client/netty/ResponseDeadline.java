/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.RawRequestOptions;
import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The response timeout of one request, see {@link RawRequestOptions#getResponseTimeout()}. It
 * replaces the configured read timeout of the request until the response arrives, and fails
 * the request with a read timeout if the response does not arrive in time. The configured read
 * timeout belongs to the connection of an HTTP/1 request, and to the stream of an HTTP/2 or
 * HTTP/3 request, see {@link StreamReadTimeoutHandler}. <b>Event loop only.</b>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ResponseDeadline implements Runnable {
    /**
     * The deadline of the request on a channel (the connection of an HTTP/1 request, the stream
     * of an HTTP/2 or HTTP/3 request), while the configured read timeout does not apply to it.
     */
    private static final AttributeKey<ResponseDeadline> ACTIVE = AttributeKey.valueOf(ResponseDeadline.class, "active");

    private final ConnectionManager.PoolHandle poolHandle;
    @Nullable
    private ScheduledFuture<?> timer;
    private boolean done;

    private ResponseDeadline(ConnectionManager.PoolHandle poolHandle) {
        this.poolHandle = poolHandle;
    }

    /**
     * Start the response timeout of the request on the given handle.
     *
     * @param poolHandle The handle the request is sent on
     * @param timeout    The response timeout
     * @return The deadline, to {@link #stop()} when the response arrives or the request fails
     */
    static ResponseDeadline start(ConnectionManager.PoolHandle poolHandle, Duration timeout) {
        ResponseDeadline deadline = new ResponseDeadline(poolHandle);
        poolHandle.channel().attr(ACTIVE).set(deadline);
        deadline.timer = poolHandle.channel().eventLoop().schedule(deadline, timeout.toNanos(), TimeUnit.NANOSECONDS);
        return deadline;
    }

    /**
     * Whether the configured read timeout fails the request on the given channel: not while the
     * request has a response timeout of its own.
     *
     * @param channel The channel of the request: the connection of an HTTP/1 request, the stream
     *                of an HTTP/2 or HTTP/3 request
     * @return Whether the read timeout applies
     */
    static boolean readTimeoutApplies(Channel channel) {
        return channel.attr(ACTIVE).get() == null;
    }

    @Override
    public void run() {
        if (!done) {
            // fail the request like the configured read timeout does
            poolHandle.taint();
            Channel channel = poolHandle.channel();
            channel.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);
            // closes the connection (HTTP/1) or only this stream (HTTP/2)
            channel.close();
            stop();
        }
    }

    /**
     * The response arrived, or the request failed: cancel the timeout. The configured read
     * timeout applies again, e.g. to the reads of the response body.
     */
    void stop() {
        if (!done) {
            done = true;
            if (timer != null) {
                timer.cancel(false);
            }
            poolHandle.channel().attr(ACTIVE).compareAndSet(this, null);
        }
    }
}
