/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.websocket;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.UriRoute;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

import javax.inject.Singleton;
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
public class ServerWebSocketProcessor extends DefaultRouteBuilder implements ExecutableMethodProcessor<ServerWebSocket> {

    private Set<Class> mappedWebSockets = new HashSet<>(4);

    /**
     * Default constructor.
     *
     * @param executionHandleLocator The {@link ExecutionHandleLocator}
     * @param uriNamingStrategy The {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy}
     * @param conversionService The {@link ConversionService}
     */
    ServerWebSocketProcessor(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Class<?> beanType = beanDefinition.getBeanType();
        if (mappedWebSockets.contains(beanType)) {
            return;
        }

        if (method.isAnnotationPresent(OnMessage.class) || method.isAnnotationPresent(OnOpen.class)) {
            mappedWebSockets.add(beanType);
            String uri = beanDefinition.stringValue(ServerWebSocket.class).orElse("/ws");

            UriRoute route = GET(uri, method);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Created WebSocket: {}", route);
            }
        }
    }
}
