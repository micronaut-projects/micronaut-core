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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The configured read timeout of one request of an HTTP/2 or HTTP/3 connection, on the channel
 * of its stream. The request times out alone: only its stream is reset, and the other requests
 * of the connection go on. <b>Event loop only.</b>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StreamReadTimeoutHandler extends ReadTimeoutHandler {
    private final Connection connection;
    private boolean timedOut;

    /**
     * @param timeout    The read timeout
     * @param connection The connection of the stream
     */
    StreamReadTimeoutHandler(Duration timeout, Connection connection) {
        super(timeout.toNanos(), TimeUnit.NANOSECONDS);
        this.connection = connection;
    }

    @Override
    protected void readTimedOut(ChannelHandlerContext ctx) {
        if (timedOut) {
            return;
        }
        timedOut = true;
        // counted before the failed request releases its stream
        boolean onlyRequest = connection.liveRequests() <= 1;
        ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
        ctx.close();
        if (onlyRequest) {
            // like the read timeout of an HTTP/1 connection: a connection that stopped
            // responding is not reused
            connection.closeAfterReadTimeout();
        }
    }

    /**
     * The connection of the streams.
     */
    interface Connection {
        /**
         * @return The number of live requests of the connection
         */
        int liveRequests();

        /**
         * Close the connection, after the read timeout of its only request.
         */
        void closeAfterReadTimeout();
    }
}
