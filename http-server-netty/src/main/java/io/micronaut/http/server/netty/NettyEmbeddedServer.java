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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
                RefreshEventListener,
                NettyServerCustomizer.Registry,
                GracefulShutdownCapable {
    /**
     * Gets the set of all ports this Netty server is bound to.
     * @return An immutable set of bound ports if the server has been started with {@link #start()} an empty set otherwise.
     */
    default Set<Integer> getBoundPorts() {
        return Collections.singleton(getPort());
    }

    @Override
    @NonNull
    default NettyEmbeddedServer start() {
        return (NettyEmbeddedServer) EmbeddedServer.super.start();
    }

    @Override
    @NonNull
    default NettyEmbeddedServer stop() {
        return (NettyEmbeddedServer) EmbeddedServer.super.stop();
    }

    /**
     * Stops the Netty instance, but keeps the ApplicationContext running.
     * This for CRaC checkpointing purposes.
     * This method will only return after waiting for netty to stop.
     *
     * @return The stopped NettyEmbeddedServer
     */
    @SuppressWarnings("unused") // Used by CRaC
    @NonNull
    NettyEmbeddedServer stopServerOnly();

    @Override
    default void register(@NonNull NettyServerCustomizer customizer) {
        throw new UnsupportedOperationException();
    }

    @Override
    default CompletionStage<?> shutdownGracefully() {
        // default implementation for compatibility
        return CompletableFuture.completedStage(null);
    }
}
