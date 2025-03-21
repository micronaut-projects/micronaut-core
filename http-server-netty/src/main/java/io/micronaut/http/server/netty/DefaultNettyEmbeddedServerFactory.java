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
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.netty.channel.converters.DefaultChannelOptionFactory;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.ssl.CertificateProvidedSslBuilder;
import io.micronaut.http.server.netty.ssl.SelfSignedSslBuilder;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.server.netty.websocket.WebSocketUpgradeHandlerFactory;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

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
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final Map<Class<?>, ApplicationEventPublisher<?>> cachedEventPublishers = new ConcurrentHashMap<>(5);
    private final WebSocketUpgradeHandlerFactory webSocketUpgradeHandlerFactory;
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    private @Nullable ServerSslBuilder serverSslBuilder;
    private @Nullable ChannelOptionFactory channelOptionFactory;
    private List<ChannelOutboundHandler> outboundHandlers = Collections.emptyList();

    /**
     * Default constructor.
     * @param applicationContext The app ctx
     * @param routeExecutor The route executor
     * @param mediaTypeCodecRegistry The media type codec
     * @param messageBodyHandlerRegistry The message body handler registery
     * @param staticResourceResolver The static resource resolver
     * @param nettyThreadFactory The netty thread factory
     * @param httpCompressionStrategy The http compression strategy
     * @param eventLoopGroupFactory The event loop group factory
     * @param eventLoopGroupRegistry The event loop group registry
     * @param webSocketUpgradeHandlerFactory An optional websocket integration
     */
    protected DefaultNettyEmbeddedServerFactory(ApplicationContext applicationContext,
                                                RouteExecutor routeExecutor,
                                                MediaTypeCodecRegistry mediaTypeCodecRegistry,
                                                MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                                                StaticResourceResolver staticResourceResolver,
                                                @Named(NettyThreadFactory.NAME) ThreadFactory nettyThreadFactory,
                                                HttpCompressionStrategy httpCompressionStrategy,
                                                EventLoopGroupFactory eventLoopGroupFactory,
                                                EventLoopGroupRegistry eventLoopGroupRegistry,
                                                @Nullable WebSocketUpgradeHandlerFactory webSocketUpgradeHandlerFactory) {
        this.applicationContext = applicationContext;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
        this.requestArgumentSatisfier = routeExecutor.getRequestArgumentSatisfier();
        this.routeExecutor = routeExecutor;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.staticResourceResolver = staticResourceResolver;
        this.executorSelector = routeExecutor.getExecutorSelector();
        this.nettyThreadFactory = nettyThreadFactory;
        this.httpCompressionStrategy = httpCompressionStrategy;
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
        this.webSocketUpgradeHandlerFactory = webSocketUpgradeHandlerFactory;
    }

    @Override
    @NonNull
    public NettyEmbeddedServer build(@NonNull NettyHttpServerConfiguration configuration) {
        return buildInternal(configuration, false, null);
    }

    @Override
    @NonNull
    public NettyEmbeddedServer build(@NonNull NettyHttpServerConfiguration configuration, @Nullable ServerSslConfiguration sslConfiguration) {
        return buildInternal(configuration, false, sslConfiguration);
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
        return buildInternal(configuration, true, null);
    }

    @Override
    public MessageBodyHandlerRegistry getMessageBodyHandlerRegistry() {
        return messageBodyHandlerRegistry;
    }

    @NonNull
    private NettyEmbeddedServer buildInternal(@NonNull NettyHttpServerConfiguration configuration,
                                              boolean isDefaultServer,
                                              @Nullable ServerSslConfiguration sslConfiguration) {
        Objects.requireNonNull(configuration, "Netty HTTP server configuration cannot be null");

        if (isDefaultServer) {
            return new NettyHttpServer(
                    configuration,
                    this,
                    true
            );
        } else {
            NettyEmbeddedServices embeddedServices = resolveNettyEmbeddedServices(configuration, sslConfiguration);
            return new NettyHttpServer(
                    configuration,
                    embeddedServices,
                    false
            );
        }
    }

    private NettyEmbeddedServices resolveNettyEmbeddedServices(@NonNull NettyHttpServerConfiguration configuration,
                                                               @Nullable ServerSslConfiguration sslConfiguration) {
        if (sslConfiguration != null && sslConfiguration.isEnabled()) {
            ServerSslBuilder resolvedSslBuilder;
            final ResourceResolver resourceResolver = applicationContext.getBean(ResourceResolver.class);
            if (sslConfiguration.buildSelfSigned()) {
                resolvedSslBuilder = new SelfSignedSslBuilder(
                      configuration,
                      sslConfiguration,
                      resourceResolver
                );
            } else {
                resolvedSslBuilder = new CertificateProvidedSslBuilder(
                    configuration,
                    sslConfiguration,
                    resourceResolver
                );
            }
            return new DelegateNettyEmbeddedServices() {
                @Override
                public NettyEmbeddedServices getDelegate() {
                    return DefaultNettyEmbeddedServerFactory.this;
                }

                @Override
                public ServerSslBuilder getServerSslBuilder() {
                    return resolvedSslBuilder;
                }
            };
        }
        return this;
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
    public Optional<NettyServerWebSocketUpgradeHandler> getWebSocketUpgradeHandler(NettyEmbeddedServer server) {
        return Optional.ofNullable(webSocketUpgradeHandlerFactory)
                        .map(factory -> factory.create(server, this));
    }

    @Override
    public EventLoopGroupRegistry getEventLoopGroupRegistry() {
        return eventLoopGroupRegistry;
    }

    @Override
    public EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration configuration) {
        return new MultiThreadIoEventLoopGroup(
            configuration.getNumThreads(),
            nettyThreadFactory,
            eventLoopGroupFactory.createIoHandlerFactory(configuration)
        );
    }

    @Override
    public Channel getChannelInstance(NettyChannelType type, EventLoopGroupConfiguration workerConfig) {
        return eventLoopGroupFactory.channelInstance(type, workerConfig);
    }

    @Override
    public Channel getChannelInstance(NettyChannelType type, EventLoopGroupConfiguration workerConfig, Channel parent, int fd) {
        return eventLoopGroupFactory.channelInstance(type, workerConfig, parent, fd);
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
