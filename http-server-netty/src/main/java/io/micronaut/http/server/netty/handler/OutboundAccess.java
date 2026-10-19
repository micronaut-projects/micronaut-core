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
     * Give up on the response of this request, after writing it failed in a way that may have
     * left a part of it on the connection, so that no other response can be written in its
     * place: HTTP/1.1 closes the connection, HTTP/2 resets the stream. Does nothing if the
     * response was written completely.
     *
     * @since 5.2.16
     */
    void abort();
}
