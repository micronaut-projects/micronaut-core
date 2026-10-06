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
     * Run a task, once, if the request is abandoned before its response is written: its
     * connection closes, or, over HTTP/2, its stream is reset or closed. A component that holds
     * the request while it computes the response, e.g. an asynchronous direct route, cancels its
     * work with it. The task runs on the event loop of the connection, at once if the request is
     * already abandoned. By default it never runs.
     *
     * @param task The task
     * @return Unregisters the task, e.g. once the response is ready to be written; call it on the
     * event loop
     * @since 5.3.0
     */
    default Runnable onAbandoned(Runnable task) {
        return () -> { };
    }
}
