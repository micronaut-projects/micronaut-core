/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.body.NettyWriteContext;

/**
 * @since 4.4.0
 */
@Internal
public interface OutboundAccess extends NettyWriteContext {
    /**
     * Register an attachment that will be passed to {@link RequestHandler#responseWritten(Object)}.
     *
     * @param attachment The attachment
     */
    void attachment(Object attachment);

    /**
     * Close this HTTP/1.1 connection after this response has been written, e.g. when there's an
     * unrecoverable error that may corrupt future requests. This method has no effect on HTTP/2.
     */
    void closeAfterWrite();

    /**
     * Write the next response as it is, never compressed, see
     * {@link io.micronaut.http.server.ServerResponseAttributes#SKIP_COMPRESSION}.
     *
     * @since 5.3.0
     */
    default void skipCompression() {
    }

    /**
     * Run a callback when the request is abandoned before its response is written: the client
     * closed the HTTP/1.1 connection, or reset the HTTP/2 stream of the request, or the HTTP/2
     * stream closed with its connection. The callback runs once, on the event loop.
     *
     * @param callback The callback
     * @return Removes the callback, e.g. when it is not needed any more; may be called from any thread
     * @since 5.3.0
     */
    Runnable whenAbandoned(Runnable callback);
}
