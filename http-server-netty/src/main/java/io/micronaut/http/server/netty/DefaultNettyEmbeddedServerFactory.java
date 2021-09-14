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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.netty.channel.converters.DefaultChannelOptionFactory;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.http.server.netty.types.DefaultCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.files.FileTypeHandler;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of {@link io.micronaut.http.server.netty.NettyEmbeddedServerFactory}.
 *
 * @author graemerocher
 * @since 3.1.0
 */
@Factory
@Internal
// prevents exposing NettyEmbeddedServices
@Bean(typed = {NettyEmbeddedServerFactory.class, DefaultNettyEmbeddedServerFactory.class})
public class DefaultNettyEmbeddedServerFactory
        implements NettyEmbeddedServerFactory,
                   NettyEmbeddedServices {
    private final ApplicationContext applicationContext;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final RouteExecutor routeExecutor;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final StaticResourceResolver staticResourceResolver;
    private final ExecutorSelector executorSelector;
    private final ThreadFactory nettyThreadFactory;
    private final HttpCompressionStrategy httpCompressionStrategy;
    private final WebSocketBeanRegistry websocketBeanRegistry;
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final Map<Class<?>, ApplicationEventPublisher<?>> cachedEventPublishers = new ConcurrentHashMap<>(5);
    private @Nullable ServerSslBuilder serverSslBuilder;
    private @Nullable ChannelOptionFactory channelOptionFactory;
    private List<ChannelOutboundHandler> outboundHandlers = Collections.emptyList();

    /**
     * Default constructor.
     * @param applicationContext The app ctx
     * @param routeExecutor The route executor
     * @param mediaTypeCodecRegistry The media type codec
     * @param staticResourceResolver The static resource resolver
     * @param nettyThreadFactory The netty thread factory
     * @param httpCompressionStrategy The http compression strategy
     * @param eventLoopGroupFactory The event loop group factory
     * @param eventLoopGroupRegistry The event loop group registry
     */
    protected DefaultNettyEmbeddedServerFactory(ApplicationContext applicationContext,
                                                RouteExecutor routeExecutor,
                                                MediaTypeCodecRegistry mediaTypeCodecRegistry,
                                                StaticResourceResolver staticResourceResolver,
                                                @Named(NettyThreadFactory.NAME) ThreadFactory nettyThreadFactory,
                                                HttpCompressionStrategy httpCompressionStrategy,
                                                EventLoopGroupFactory eventLoopGroupFactory,
                                                EventLoopGroupRegistry eventLoopGroupRegistry) {
        this.applicationContext = applicationContext;
        this.requestArgumentSatisfier = routeExecutor.getRequestArgumentSatisfier();
        this.routeExecutor = routeExecutor;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.staticResourceResolver = staticResourceResolver;
        this.executorSelector = routeExecutor.getExecutorSelector();
        this.nettyThreadFactory = nettyThreadFactory;
        this.httpCompressionStrategy = httpCompressionStrategy;
        this.websocketBeanRegistry = WebSocketBeanRegistry.forServer(applicationContext);
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
    }

    @Override
    @NonNull
    public NettyEmbeddedServer build(@NonNull NettyHttpServerConfiguration configuration) {
        return buildInternal(configuration, false);
    }

    /**
     * Builds the default server configuration.
     * @param configuration The server configuration
     * @return The {@link io.micronaut.http.server.netty.NettyEmbeddedServer} instance
     */
    @Singleton
    @Primary
    @NonNull
    protected NettyEmbeddedServer buildDefaultServer(@NonNull NettyHttpServerConfiguration configuration) {
        return buildInternal(configuration, true);
    }

    @NotNull
    private NettyEmbeddedServer buildInternal(@NonNull NettyHttpServerConfiguration configuration,
                                              boolean isDefaultServer) {
        Objects.requireNonNull(configuration, "Netty HTTP server configuration cannot be null");
        List<NettyCustomizableResponseTypeHandler<?>> handlers = Arrays.asList(
                new FileTypeHandler(configuration.getFileTypeHandlerConfiguration()),
                new StreamTypeHandler()
        );
        return new NettyHttpServer(
                configuration,
                this,
                new DefaultCustomizableResponseTypeHandlerRegistry(handlers.toArray(new NettyCustomizableResponseTypeHandler[0])),
                isDefaultServer
        );
    }

    @Override
    public List<ChannelOutboundHandler> getOutboundHandlers() {
        return outboundHandlers;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public RequestArgumentSatisfier getRequestArgumentSatisfier() {
        return requestArgumentSatisfier;
    }

    @Override
    public RouteExecutor getRouteExecutor() {
        return routeExecutor;
    }

    @Override
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    @Override
    public StaticResourceResolver getStaticResourceResolver() {
        return staticResourceResolver;
    }

    @Override
    public ExecutorSelector getExecutorSelector() {
        return executorSelector;
    }

    @Override
    public ServerSslBuilder getServerSslBuilder() {
        return serverSslBuilder;
    }

    @Override
    public ChannelOptionFactory getChannelOptionFactory() {
        if (channelOptionFactory == null) {
            channelOptionFactory = new DefaultChannelOptionFactory();
        }
        return channelOptionFactory;
    }

    @Override
    public HttpCompressionStrategy getHttpCompressionStrategy() {
        return httpCompressionStrategy;
    }

    @Override
    public WebSocketBeanRegistry getWebSocketBeanRegistry() {
        return this.websocketBeanRegistry;
    }

    @Override
    public EventLoopGroupRegistry getEventLoopGroupRegistry() {
        return eventLoopGroupRegistry;
    }

    @Override
    public EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration config) {
        return eventLoopGroupFactory.createEventLoopGroup(
                config,
                this.nettyThreadFactory
        );
    }

    @Override
    public ServerSocketChannel getServerSocketChannelInstance(EventLoopGroupConfiguration workerConfig) {
        return eventLoopGroupFactory.serverSocketChannelInstance(workerConfig);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E> ApplicationEventPublisher<E> getEventPublisher(Class<E> eventClass) {
        Objects.requireNonNull(eventClass, "Event class cannot be null");
        return (ApplicationEventPublisher<E>) cachedEventPublishers
                .computeIfAbsent(eventClass, applicationContext::getEventPublisher);
    }

    @Override
    @NonNull
    public EventLoopGroup createEventLoopGroup(int numThreads, @NonNull ExecutorService executorService, Integer ioRatio) {
        return eventLoopGroupFactory.createEventLoopGroup(
                numThreads,
                executorService,
                ioRatio
        );
    }

    /**
     * Configures the channel option factory.
     * @param channelOptionFactory The channel option factory.
     */
    @Inject
    protected void setChannelOptionFactory(@Nullable ChannelOptionFactory channelOptionFactory) {
        this.channelOptionFactory = channelOptionFactory;
    }

    /**
     * Configures the {@link io.micronaut.http.server.netty.ssl.ServerSslBuilder} the server ssl builder.
     * @param serverSslBuilder The builder
     */
    @Inject
    protected void setServerSslBuilder(@Nullable ServerSslBuilder serverSslBuilder) {
        this.serverSslBuilder = serverSslBuilder;
    }

    /**
     * Sets the outbound handlers.
     * @param outboundHandlers The {@link io.netty.channel.ChannelOutboundHandler} instances
     */
    @Inject
    protected void setOutboundHandlers(List<ChannelOutboundHandler> outboundHandlers) {
        if (CollectionUtils.isNotEmpty(outboundHandlers)) {
            OrderUtil.sort(outboundHandlers);
            this.outboundHandlers = Collections.unmodifiableList(outboundHandlers);
        }
    }
}
