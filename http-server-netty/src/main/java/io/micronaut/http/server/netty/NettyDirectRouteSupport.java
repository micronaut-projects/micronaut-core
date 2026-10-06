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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.web.router.direct.DirectRouteSupport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import jakarta.inject.Singleton;

/**
 * Declares that the Netty server answers direct routes: {@link RoutingInBoundHandler} looks them
 * up for each request it receives, see {@link NettyDirectRoutes}. A Netty buffer given as the
 * body of a direct route's response value is copied once, as writing it releases it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class NettyDirectRouteSupport implements DirectRouteSupport {

    /**
     * A {@link ByteBuf}, or a buffer over one, is released once written: the route keeps a copy
     * of its readable bytes, written for every request, and the buffer is released.
     */
    @Override
    public Object shareableBody(Object body) {
        ByteBuf buf;
        if (body instanceof ByteBuf b) {
            buf = b;
        } else if (body instanceof io.micronaut.core.io.buffer.ByteBuffer<?> buffer && buffer.asNativeBuffer() instanceof ByteBuf b) {
            buf = b;
        } else {
            return body;
        }
        try {
            return ByteBufUtil.getBytes(buf);
        } finally {
            buf.release();
        }
    }
}
