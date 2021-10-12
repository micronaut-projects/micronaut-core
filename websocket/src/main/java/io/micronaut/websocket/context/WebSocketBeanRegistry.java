/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.websocket.context;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.ServerWebSocket;

/**
 * Registry for WebSocket beans.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketBeanRegistry {
    /**
     * An empty registry.
     */
    WebSocketBeanRegistry EMPTY = new WebSocketBeanRegistry() {
        @Override
        public <T> WebSocketBean<T> getWebSocket(Class<T> type) {
            throw new NoSuchBeanException(type);
        }
    };

    /**
     * Retrieves a {@link WebSocketBean}.
     *
     * @param type The type
     * @param <T> The generic type
     * @return The {@link WebSocketBean}
     * @throws io.micronaut.context.exceptions.NoSuchBeanException if the bean doesn't exist
     */
    <T> WebSocketBean<T> getWebSocket(Class<T> type);

    /**
     * Create a {@link WebSocketBeanRegistry} from the given bean context.
     *
     * @param beanContext The bean context
     * @return The {@link WebSocketBeanRegistry}
     */
    static WebSocketBeanRegistry forServer(BeanContext beanContext) {
        return new DefaultWebSocketBeanRegistry(beanContext, ServerWebSocket.class);
    }

    /**
     * Create a {@link WebSocketBeanRegistry} from the given bean context.
     *
     * @param beanContext The bean context
     * @return The {@link WebSocketBeanRegistry}
     */
    static WebSocketBeanRegistry forClient(BeanContext beanContext) {
        return new DefaultWebSocketBeanRegistry(beanContext, ClientWebSocket.class);
    }
}
