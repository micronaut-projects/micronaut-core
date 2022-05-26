/*
 * Copyright 2017-2021 original authors
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

import java.util.Collections;
import java.util.Set;

import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener;
import io.micronaut.runtime.server.EmbeddedServer;

/**
 * Extended {@link io.micronaut.runtime.server.EmbeddedServer} interface that represents a
 * Netty-based HTTP server.
 *
 * @author graemerocher
 * @since 3.1.0
 */
public interface NettyEmbeddedServer
        extends EmbeddedServer,
                WebSocketSessionRepository,
                ChannelPipelineCustomizer,
                RefreshEventListener {
    /**
     * Gets the set of all ports this Netty server is bound to.
     * @return An immutable set of bound ports if the server has been started with {@link #start()} an empty set otherwise.
     */
    default Set<Integer> getBoundPorts() {
        return Collections.singleton(getPort());
    }

    @Override
    default NettyEmbeddedServer start() {
        return (NettyEmbeddedServer) EmbeddedServer.super.start();
    }

    @Override
    default NettyEmbeddedServer stop() {
        return (NettyEmbeddedServer) EmbeddedServer.super.stop();
    }
}
