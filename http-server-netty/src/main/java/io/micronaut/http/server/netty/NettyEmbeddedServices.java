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

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;

/**
 * Internal interface with services required by the {@link io.micronaut.http.server.netty.NettyHttpServer}.
 *e
 * @author graemerocher
 * @since 3.1.0
 */
@Internal
public interface NettyEmbeddedServices {
    /**
     * @return The channel outbound handlers
     */
    @NonNull
    List<ChannelOutboundHandler> getOutboundHandlers();

    /**
     * @return The application context
     */
    @NonNull
    ApplicationContext getApplicationContext();

    /**
     * @return The request argument satisfier
     * @see io.micronaut.http.server.binding.RequestArgumentSatisfier
     */
    @NonNull
    default RequestArgumentSatisfier getRequestArgumentSatisfier() {
        return getRouteExecutor().getRequestArgumentSatisfier();
    }

    /**
     * @return The route executor
     * @see io.micronaut.http.server.RouteExecutor
     */
    @NonNull
    RouteExecutor getRouteExecutor();

    /**
     * @return The media type code registry
     * @see io.micronaut.http.codec.MediaTypeCodecRegistry
     */
    @NonNull
    MediaTypeCodecRegistry getMediaTypeCodecRegistry();

    /**
     * @return The static resource resolver
     * @see io.micronaut.web.router.resource.StaticResourceResolver
     */
    @NonNull
    StaticResourceResolver getStaticResourceResolver();

    /**
     * @return The executor resolver
     */
    @NonNull
    default ExecutorSelector getExecutorSelector() {
        return getRouteExecutor().getExecutorSelector();
    }

    /**
     * @return The server SSL builder or {@code null} if none is configured
     * @see io.micronaut.http.server.netty.ssl.CertificateProvidedSslBuilder
     */
    @Nullable
    ServerSslBuilder getServerSslBuilder();

    /**
     * @return The channel option factory
     */
    @NonNull
    ChannelOptionFactory getChannelOptionFactory();

    /**
     * @return The http compression strategy
     */
    @NonNull
    HttpCompressionStrategy getHttpCompressionStrategy();

    /**
     * @return The websocket bean registry
     */
    @NonNull
    WebSocketBeanRegistry getWebSocketBeanRegistry();

    /**
     * @return The event loop group registry.
     */
    @NonNull
    EventLoopGroupRegistry getEventLoopGroupRegistry();

    /**
     * @return Obtains the router
     */
    default @NonNull Router getRouter() {
        return getRouteExecutor().getRouter();
    }

    /**
     * Creates the event loop group configuration.
     * @param config The config
     * @return The event loop group config
     */
    @NonNull EventLoopGroup createEventLoopGroup(@NonNull EventLoopGroupConfiguration config);

    /**
     * Creates the event loop group configuration.
     * @param numThreads The number of threads
     * @param executorService The executor service
     * @param ioRatio The I/O ratio
     * @return The event loop group
     */
    @NonNull EventLoopGroup createEventLoopGroup(int numThreads, @NonNull ExecutorService executorService, @Nullable  Integer ioRatio);

    /**
     * Gets the server socket channel instance.
     * @param workerConfig The worker config
     * @return The {@link io.netty.channel.socket.ServerSocketChannel}
     */
    @NonNull ServerSocketChannel getServerSocketChannelInstance(@NonNull EventLoopGroupConfiguration workerConfig);

    /**
     * Get an event publisher for the server for the given type.
     * @param eventClass The event publisher
     * @param <E> The event generic type
     * @return The event publisher
     */
    @NonNull <E> ApplicationEventPublisher<E> getEventPublisher(@NonNull Class<E> eventClass);
}
