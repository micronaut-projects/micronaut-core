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
package io.micronaut.http.server.websocket;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.UriRoute;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A processor that exposes WebSocket URIs via the router.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
@Requires(classes = {ServerWebSocket.class, WebSocketBeanRegistry.class})
public class ServerWebSocketProcessor extends DefaultRouteBuilder implements BeanDefinitionProcessor<ServerWebSocket> {

    private final Set<Class<?>> mappedWebSockets = new HashSet<>(4);

    /**
     * Default constructor.
     *
     * @param executionHandleLocator The {@link ExecutionHandleLocator}
     * @param uriNamingStrategy      The {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy}
     * @param conversionService      The {@link ConversionService}
     */
    ServerWebSocketProcessor(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext beanContext) {
        Class<?> beanType = beanDefinition.getBeanType();
        if (mappedWebSockets.contains(beanType)) {
            return;
        }
        ExecutableMethod<?, ?> target = routeTarget(beanDefinition);
        if (target == null) {
            return;
        }
        mappedWebSockets.add(beanType);
        String uri = beanDefinition.stringValue(ServerWebSocket.class).orElse("/ws");

        // a single route per WebSocket, which only an upgrade request matches: a plain HTTP
        // request to the same path is left to the other routes
        UriRoute route = GET(uri, target).where(WebSocketUpgradeCondition.INSTANCE);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created WebSocket: {}", route);
        }
    }

    /**
     * The method the route of a WebSocket targets: the {@link OnOpen} method, which the upgrade
     * request opens the WebSocket with, or else the {@link OnMessage} method. Its annotations are
     * the annotations of the route, e.g. for filters and route conditions.
     *
     * @param beanDefinition The bean definition of the WebSocket
     * @return The method, or {@code null} if the WebSocket has neither
     */
    private static @Nullable ExecutableMethod<?, ?> routeTarget(BeanDefinition<?> beanDefinition) {
        ExecutableMethod<?, ?> onMessage = null;
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (method.isAnnotationPresent(OnOpen.class)) {
                return method;
            }
            if (onMessage == null && method.isAnnotationPresent(OnMessage.class)) {
                onMessage = method;
            }
        }
        return onMessage;
    }
}
