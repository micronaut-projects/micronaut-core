/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.http.server.netty.types;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.server.netty.NettyHttpResponse;

/**
 * Represents a class that is designed to handle specific types
 * that are returned from routes in a netty specific way.
 *
 * @param <T> The type to be handled
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface NettySpecialTypeHandler<T> extends Ordered {

    /**
     * Responsible for fully handling the response, including any closing of the channel.
     *
     * @param object The object to be handled
     * @param request The native Netty request
     * @param response The mutable Particle response
     * @param context The channel context
     */
    void handle(T object, HttpRequest request, NettyHttpResponse response, ChannelHandlerContext context);

    /**
     * @param type The type to check
     * @return True if the handler supports handling the given type
     */
    boolean supports(Class<?> type);
}
