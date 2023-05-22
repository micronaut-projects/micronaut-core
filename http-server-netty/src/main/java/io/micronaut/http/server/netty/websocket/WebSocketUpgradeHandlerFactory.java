/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import io.micronaut.http.server.netty.NettyEmbeddedServices;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import jakarta.inject.Singleton;

/**
 * Creates the inbound handler for websocket upgrade requests.
 *
 * @author graemerocher
 * @since 4.0.0
 */
@Requires(classes = WebSocketBeanRegistry.class)
@Singleton
@Internal
public final class WebSocketUpgradeHandlerFactory {
    private final ConversionService conversionService;
    private final NettyHttpServerConfiguration serverConfiguration;

    public WebSocketUpgradeHandlerFactory(ConversionService conversionService, NettyHttpServerConfiguration serverConfiguration) {
        this.conversionService = conversionService;
        this.serverConfiguration = serverConfiguration;
    }

    /**
     * Creates the websocket upgrade inbound handler.
     *
     * @param embeddedServer        The server
     * @param nettyEmbeddedServices The services
     * @return The handler
     */
    public NettyServerWebSocketUpgradeHandler create(NettyEmbeddedServer embeddedServer, NettyEmbeddedServices nettyEmbeddedServices) {
        return new NettyServerWebSocketUpgradeHandler(nettyEmbeddedServices, embeddedServer, conversionService, serverConfiguration);
    }
}
