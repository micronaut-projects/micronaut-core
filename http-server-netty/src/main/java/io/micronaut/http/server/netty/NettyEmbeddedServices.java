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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.ssl.NettyServerSslFactory;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.ssl.CertificateProvider;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Internal interface with services required by the {@link io.micronaut.http.server.netty.NettyHttpServer}.
 *e
 * @author graemerocher
 * @since 3.1.0
 */
@Internal
public interface NettyEmbeddedServices {
    /**
     * @return The message body handler registry.
     */
    MessageBodyHandlerRegistry getMessageBodyHandlerRegistry();

    /**
     * @return The channel outbound handlers
     */
    List<ChannelOutboundHandler> getOutboundHandlers();

    /**
     * @return The application context
     */
    ApplicationContext getApplicationContext();

    /**
     * @return The request argument satisfier
     * @see io.micronaut.http.server.binding.RequestArgumentSatisfier
     */
    default RequestArgumentSatisfier getRequestArgumentSatisfier() {
        return getRouteExecutor().getRequestArgumentSatisfier();
    }

    /**
     * @return The route executor
     * @see io.micronaut.http.server.RouteExecutor
     */
    RouteExecutor getRouteExecutor();

    /**
     * @return The media type code registry
     * @see io.micronaut.http.codec.MediaTypeCodecRegistry
     */
    MediaTypeCodecRegistry getMediaTypeCodecRegistry();

    /**
     * @return The static resource resolver
     * @see io.micronaut.web.router.resource.StaticResourceResolver
     */
    StaticResourceResolver getStaticResourceResolver();

    /**
     * @return The executor resolver
     */
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
    ChannelOptionFactory getChannelOptionFactory();

    /**
     * @return The http compression strategy
     */
    HttpCompressionStrategy getHttpCompressionStrategy();

    /**
     * @param embeddedServer The server
     * @return The websocket upgrade handler if present
     */
    @SuppressWarnings("java:S1452")
    Optional<NettyServerWebSocketUpgradeHandler> getWebSocketUpgradeHandler(NettyEmbeddedServer embeddedServer);

    /**
     * @return The event loop group registry.
     */
    EventLoopGroupRegistry getEventLoopGroupRegistry();

    /**
     * @return Obtains the router
     */
    default Router getRouter() {
        return getRouteExecutor().getRouter();
    }

    /**
     * Creates the event loop group configuration.
     * @param config The config
     * @return The event loop group config
     */
    EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration config);

    /**
     * Creates the event loop group configuration.
     * @param numThreads The number of threads
     * @param executorService The executor service
     * @param ioRatio The I/O ratio
     * @return The event loop group
     */
    EventLoopGroup createEventLoopGroup(int numThreads, ExecutorService executorService, @Nullable  Integer ioRatio);

    /**
     * Gets the server socket channel instance.
     *
     * @param workerConfig The worker config
     * @return The {@link io.netty.channel.socket.ServerSocketChannel}
     * @deprecated Use {@link #getChannelInstance(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default ServerSocketChannel getServerSocketChannelInstance(EventLoopGroupConfiguration workerConfig) {
        return (ServerSocketChannel) getChannelInstance(NettyChannelType.SERVER_SOCKET, workerConfig);
    }

    /**
     * Gets the domain server socket channel instance.
     * @param workerConfig The worker config
     * @return The {@link io.netty.channel.unix.DomainSocketChannel}
     * @throws UnsupportedOperationException if domain sockets are not supported.
     * @deprecated Use {@link #getChannelInstance(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default ServerChannel getDomainServerChannelInstance(EventLoopGroupConfiguration workerConfig) {
        return (ServerChannel) getChannelInstance(NettyChannelType.DOMAIN_SERVER_SOCKET, workerConfig);
    }

    /**
     * Gets the domain server socket channel instance.
     * @param type The channel type to return
     * @param workerConfig The worker config
     * @return The channel
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    default Channel getChannelInstance(NettyChannelType type, EventLoopGroupConfiguration workerConfig) {
        return switch (type) {
            case SERVER_SOCKET -> getServerSocketChannelInstance(workerConfig);
            case DOMAIN_SERVER_SOCKET -> getDomainServerChannelInstance(workerConfig);
            default -> throw new UnsupportedOperationException("Unsupported netty channel type");
        };
    }

    /**
     * Gets the domain server socket channel instance.
     * @param type The channel type to return
     * @param workerConfig The worker config
     * @param parent The parent channel, or {@code null} for no parent channel
     * @param fd The pre-defined file descriptor
     * @return The channel
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    default Channel getChannelInstance(NettyChannelType type, EventLoopGroupConfiguration workerConfig, Channel parent, int fd) {
        throw new UnsupportedOperationException("File descriptor channels not supported");
    }

    /**
     * Get an event publisher for the server for the given type.
     * @param eventClass The event publisher
     * @param <E> The event generic type
     * @return The event publisher
     */
    <E> ApplicationEventPublisher<E> getEventPublisher(Class<E> eventClass);

    NettyServerSslFactory getSslFactory();

    BeanProvider<CertificateProvider> getCertificateProviders();
}
