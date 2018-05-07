/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty.types;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link NettyCustomizableResponseTypeHandler} instances.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class DefaultCustomizableResponseTypeHandlerRegistry implements NettyCustomizableResponseTypeHandlerRegistry {

    private NettyCustomizableResponseTypeHandler[] handlers;
    private ConcurrentHashMap<Class<?>, NettyCustomizableResponseTypeHandler> handlerCache = new ConcurrentHashMap<>(5);

    /**
     * @param typeHandlers The Netty customizable response type handlers
     */
    public DefaultCustomizableResponseTypeHandlerRegistry(NettyCustomizableResponseTypeHandler... typeHandlers) {
        this.handlers = typeHandlers;
    }

    @Override
    public Optional<NettyCustomizableResponseTypeHandler> findTypeHandler(Class<?> type) {
        return Optional
            .ofNullable(handlerCache.computeIfAbsent(type, (clazz) ->
                Arrays
                    .stream(handlers)
                    .filter(handler -> handler.supports(clazz))
                    .findFirst()
                    .orElse(null))
            );
    }
}
