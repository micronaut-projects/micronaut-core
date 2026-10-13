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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.http.HttpMethod;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingExecutableMethod;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.UriRoute;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import jakarta.inject.Singleton;

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
        ExecutableMethod<?, ?> onOpen = null;
        ExecutableMethod<?, ?> onMessage = null;
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (onOpen == null && method.isAnnotationPresent(OnOpen.class)) {
                onOpen = method;
            } else if (onMessage == null && method.isAnnotationPresent(OnMessage.class)) {
                onMessage = method;
            }
        }
        // the route targets the @OnOpen method, which the upgrade request opens the WebSocket
        // with, or else the @OnMessage method
        ExecutableMethod<?, ?> target = onOpen != null ? onOpen : onMessage;
        if (target == null) {
            return;
        }
        mappedWebSockets.add(beanType);
        String uri = beanDefinition.stringValue(ServerWebSocket.class).orElse("/ws");

        ExecutableMethod<Object, Object> method = (ExecutableMethod<Object, Object>) target;
        if (onOpen != null && onMessage != null) {
            // the upgrade request opens the WebSocket for both methods: the annotations of both,
            // e.g. for security, filters and route conditions, are the annotations of the route
            method = new WebSocketRouteMethod(method, new AnnotationMetadataHierarchy(
                onMessage.getAnnotationMetadata(),
                onOpen.getAnnotationMetadata()
            ));
        }
        MethodExecutionHandle<Object, Object> handle = (MethodExecutionHandle<Object, Object>) executionHandleLocator
            .createExecutionHandle(beanDefinition, method);

        // a single route per WebSocket, which only an upgrade request matches: a plain HTTP
        // request to the same path is left to the other routes. The upgrade condition is a
        // declared condition of the route, which is tested before the @RouteCondition of the
        // methods, so that a plain request never evaluates the conditions of the WebSocket
        UriRoute route = buildRoute(HttpMethod.GET, uri, handle);
        if (route instanceof RouteAssembly.DefaultUriRoute defaultRoute) {
            defaultRoute.settings().where(RouteCondition.custom(WebSocketUpgradeCondition.INSTANCE));
        } else {
            route = route.where(WebSocketUpgradeCondition.INSTANCE);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created WebSocket: {}", route);
        }
    }

    /**
     * The target of the route of a WebSocket with both an {@link OnOpen} and an
     * {@link OnMessage} method: the {@link OnOpen} method, with the annotations of both.
     *
     * @param getTarget          The {@link OnOpen} method
     * @param annotationMetadata The annotations of both methods
     */
    private record WebSocketRouteMethod(ExecutableMethod<Object, Object> getTarget,
                                        AnnotationMetadata annotationMetadata) implements DelegatingExecutableMethod<Object, Object> {

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public String toString() {
            return getTarget.toString();
        }
    }
}
