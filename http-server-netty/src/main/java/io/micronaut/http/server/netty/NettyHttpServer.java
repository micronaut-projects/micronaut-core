/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty;

import io.micronaut.http.netty.stream.HttpStreamsServerHandler;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.discovery.event.ServiceShutdownEvent;
import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.decoders.HttpRequestDecoder;
import io.micronaut.http.server.netty.encoders.HttpResponseEncoder;
import io.micronaut.http.server.netty.ssl.NettyServerSslBuilder;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Implements the bootstrap and configuration logic for the Netty implementation of {@link EmbeddedServer}.
 *
 * @author Graeme Rocher
 * @see RoutingInBoundHandler
 * @since 1.0
 */
@Singleton
public class NettyHttpServer implements EmbeddedServer, WebSocketSessionRepository {
    public static final String HTTP_STREAMS_CODEC = "http-streams-codec";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_CODEC = "http-codec";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_COMPRESSOR = "http-compressor";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_KEEP_ALIVE_HANDLER = "http-keep-alive-handler";
    @SuppressWarnings("WeakerAccess")
    public static final String MICRONAUT_HANDLER = "micronaut-inbound-handler";
    @SuppressWarnings("WeakerAccess")
    public static final String OUTBOUND_KEY = "-outbound-";

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final ExecutorService ioExecutor;
    private final ExecutorSelector executorSelector;
    private final List<ChannelOutboundHandler> outboundHandlers;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final ServerSslConfiguration sslConfiguration;
    private final StaticResourceResolver staticResourceResolver;
    private final Environment environment;
    private final Router router;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final BeanLocator beanLocator;
    private final ThreadFactory threadFactory;
    private final WebSocketBeanRegistry webSocketBeanRegistry;
    private volatile int serverPort;
    private final ApplicationContext applicationContext;
    private final SslContext sslContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ChannelGroup webSocketSessions = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private NioEventLoopGroup workerGroup;
    private NioEventLoopGroup parentGroup;
    private EmbeddedServerInstance serviceInstance;

    /**
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param applicationContext                      The application context
     * @param router                                  The router
     * @param requestArgumentSatisfier                The request argument satisfier
     * @param mediaTypeCodecRegistry                  The Media type codec registry
     * @param customizableResponseTypeHandlerRegistry The Netty customizable response type handler registry
     * @param resourceResolver                        The static resource resolver
     * @param ioExecutor                              The IO executor
     * @param threadFactory                           The thread factory
     * @param executorSelector                        The executor selector
     * @param nettyServerSslBuilder                   The Netty Server SSL builder
     * @param outboundHandlers                        The outbound handlers
     */
    @SuppressWarnings("ParameterNumber")
    @Inject
    public NettyHttpServer(
        NettyHttpServerConfiguration serverConfiguration,
        ApplicationContext applicationContext,
        Router router,
        RequestArgumentSatisfier requestArgumentSatisfier,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
        StaticResourceResolver resourceResolver,
        @javax.inject.Named(TaskExecutors.IO) ExecutorService ioExecutor,
        @javax.inject.Named(NettyThreadFactory.NAME) ThreadFactory threadFactory,
        ExecutorSelector executorSelector,
        NettyServerSslBuilder nettyServerSslBuilder,
        List<ChannelOutboundHandler> outboundHandlers
    ) {
        Optional<File> location = serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir -> DiskFileUpload.baseDirectory = dir.getAbsolutePath());
        this.applicationContext = applicationContext;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanLocator = applicationContext;
        this.environment = applicationContext.getEnvironment();
        this.serverConfiguration = serverConfiguration;
        this.sslConfiguration = nettyServerSslBuilder.getSslConfiguration();
        this.router = router;
        this.ioExecutor = ioExecutor;
        int port = sslConfiguration.isEnabled() ? sslConfiguration.getPort() : serverConfiguration.getPort();
        this.serverPort = port == -1 ? SocketUtils.findAvailableTcpPort() : port;
        this.executorSelector = executorSelector;
        OrderUtil.sort(outboundHandlers);
        this.outboundHandlers = outboundHandlers;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.staticResourceResolver = resourceResolver;
        this.sslContext = nettyServerSslBuilder.build().orElse(null);
        this.threadFactory = threadFactory;
        this.webSocketBeanRegistry = WebSocketBeanRegistry.forServer(applicationContext);
    }

    /**
     * @return The configuration for the server
     */
    @SuppressWarnings("WeakerAccess")
    public NettyHttpServerConfiguration getServerConfiguration() {
        return serverConfiguration;
    }

    @Override
    public boolean isRunning() {
        return running.get() && !SocketUtils.isTcpPortAvailable(serverPort);
    }

    @Override
    public synchronized EmbeddedServer start() {
        if (!isRunning()) {
            workerGroup = createWorkerEventLoopGroup();
            parentGroup = createParentEventLoopGroup();
            ServerBootstrap serverBootstrap = createServerBootstrap();

            processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
            processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);

            serverBootstrap = serverBootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        if (sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        }

                        serverConfiguration.getLogLevel().ifPresent(logLevel ->
                                pipeline.addLast(new LoggingHandler(logLevel))
                        );

                        pipeline.addLast(new IdleStateHandler(
                            (int) serverConfiguration.getReadIdleTime().getSeconds(),
                            (int) serverConfiguration.getWriteIdleTime().getSeconds(),
                            (int) serverConfiguration.getIdleTime().getSeconds()));

                        pipeline.addLast(HTTP_CODEC, new HttpServerCodec(
                                serverConfiguration.getMaxInitialLineLength(),
                                serverConfiguration.getMaxHeaderSize(),
                                serverConfiguration.getMaxChunkSize(),
                                serverConfiguration.isValidateHeaders(),
                                serverConfiguration.getInitialBufferSize()
                        ));

                        pipeline.addLast(new FlowControlHandler());
                        pipeline.addLast(HTTP_KEEP_ALIVE_HANDLER, new HttpServerKeepAliveHandler());
                        pipeline.addLast(HTTP_COMPRESSOR, new SmartHttpContentCompressor());
                        pipeline.addLast(HTTP_STREAMS_CODEC, new HttpStreamsServerHandler());
                        pipeline.addLast(HttpRequestDecoder.ID, new HttpRequestDecoder(
                                NettyHttpServer.this,
                                environment,
                                serverConfiguration
                        ));
                        pipeline.addLast(HttpResponseEncoder.ID, new HttpResponseEncoder(mediaTypeCodecRegistry, serverConfiguration));
                        pipeline.addLast(NettyServerWebSocketUpgradeHandler.ID, new NettyServerWebSocketUpgradeHandler(
                                getWebSocketSessionRepository(),
                                router,
                                requestArgumentSatisfier.getBinderRegistry(),
                                webSocketBeanRegistry,
                                mediaTypeCodecRegistry,
                                applicationContext
                        ));
                        pipeline.addLast(MICRONAUT_HANDLER, new RoutingInBoundHandler(
                            beanLocator,
                            router,
                            mediaTypeCodecRegistry,
                            customizableResponseTypeHandlerRegistry,
                            staticResourceResolver,
                            serverConfiguration,
                            requestArgumentSatisfier,
                            executorSelector,
                            ioExecutor
                        ));
                        registerMicronautChannelHandlers(pipeline);
                    }
                });

            Optional<String> host = serverConfiguration.getHost();

            bindServerToHost(serverBootstrap, host.orElse(null), new AtomicInteger(0));
            running.set(true);
        }

        return this;
    }

    @Override
    public synchronized EmbeddedServer stop() {
        if (isRunning() && workerGroup != null) {
            if (running.compareAndSet(true, false)) {
                try {
                    workerGroup.shutdownGracefully()
                            .addListener(this::logShutdownErrorIfNecessary);
                    parentGroup.shutdownGracefully()
                            .addListener(this::logShutdownErrorIfNecessary);
                    webSocketSessions.close();
                    applicationContext.publishEvent(new ServerShutdownEvent(this));
                    if (serviceInstance != null) {
                        applicationContext.publishEvent(new ServiceShutdownEvent(serviceInstance));
                    }
                    if (applicationContext.isRunning()) {
                        applicationContext.stop();
                    }
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error stopping Micronaut server: " + e.getMessage(), e);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public String getHost() {
        return serverConfiguration.getHost().orElse("localhost");
    }

    @Override
    public String getScheme() {
        return sslConfiguration.isEnabled() ? "https" : "http";
    }

    @Override
    public URL getURL() {
        try {
            return new URL(getScheme() + "://" + getHost() + ':' + getPort());
        } catch (MalformedURLException e) {
            throw new ConfigurationException("Invalid server URL: " + e.getMessage(), e);
        }
    }

    @Override
    public URI getURI() {
        try {
            return new URI(getScheme() + "://" + getHost() + ':' + getPort());
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Invalid server URL: " + e.getMessage(), e);
        }
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration() {
        return serverConfiguration.getApplicationConfiguration();
    }

    /**
     * @return The parent event loop group
     */
    @SuppressWarnings("WeakerAccess")
    protected NioEventLoopGroup createParentEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getParent());
    }

    /**
     * @return The worker event loop group
     */
    @SuppressWarnings("WeakerAccess")
    protected NioEventLoopGroup createWorkerEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getWorker());
    }

    /**
     * @return The Netty server bootstrap
     */
    @SuppressWarnings("WeakerAccess")
    protected ServerBootstrap createServerBootstrap() {
        return new ServerBootstrap();
    }

    @SuppressWarnings("MagicNumber")
    private void bindServerToHost(ServerBootstrap serverBootstrap, @Nullable String host, AtomicInteger attempts) {
        boolean isRandomPort = serverConfiguration.getPort() == -1;
        if (!SocketUtils.isTcpPortAvailable(serverPort) && !isRandomPort) {
            throw new ServerStartupException("Unable to start Micronaut server on port: " + serverPort, new BindException("Address already in use"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Binding server to port: {}", serverPort);
        }
        try {
            if (host != null) {
                serverBootstrap.bind(host, serverPort).sync();
            } else {
                serverBootstrap.bind(serverPort).sync();
            }

            applicationContext.publishEvent(new ServerStartupEvent(this));
            Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
            applicationName.ifPresent(id -> {
                this.serviceInstance = applicationContext.createBean(NettyEmbeddedServerInstance.class, id, this);
                applicationContext.publishEvent(new ServiceStartedEvent(serviceInstance));
            });

        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                if (e instanceof BindException) {
                    LOG.error("Unable to start server. Port already {} in use.", serverPort);
                } else {
                    LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                }
            }
            int attemptCount = attempts.getAndIncrement();

            if (isRandomPort && attemptCount < 3) {
                serverPort = SocketUtils.findAvailableTcpPort();
                bindServerToHost(serverBootstrap, host, attempts);
            } else {
                stop();
            }
        }
    }

    private void logShutdownErrorIfNecessary(Future<?> future) {
        if (!future.isSuccess()) {
            if (LOG.isWarnEnabled()) {
                Throwable e = future.cause();
                LOG.warn("Error stopping Micronaut server: " + e.getMessage(), e);
            }
        }
    }

    private NioEventLoopGroup newEventLoopGroup(NettyHttpServerConfiguration.EventLoopConfig config) {
        if (config != null) {
            Optional<ExecutorService> executorService = config.getExecutorName().flatMap(name -> beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name)));
            NioEventLoopGroup group = executorService.map(service ->
                new NioEventLoopGroup(config.getNumOfThreads(), service)
            ).orElseGet(() -> {
                if (threadFactory != null) {
                    return new NioEventLoopGroup(config.getNumOfThreads(), threadFactory);
                } else {
                    return new NioEventLoopGroup(config.getNumOfThreads());
                }
            });
            config.getIoRatio().ifPresent(group::setIoRatio);
            return group;
        } else {
            if (threadFactory != null) {
                return new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            } else {
                return new NioEventLoopGroup();
            }
        }
    }

    private void registerMicronautChannelHandlers(ChannelPipeline pipeline) {
        int i = 0;
        for (ChannelHandler outboundHandlerAdapter : outboundHandlers) {
            String name;
            if (outboundHandlerAdapter instanceof Named) {
                name = ((Named) outboundHandlerAdapter).getName();
            } else {
                name = NettyHttpServer.MICRONAUT_HANDLER + NettyHttpServer.OUTBOUND_KEY + ++i;
            }
            pipeline.addAfter(NettyHttpServer.HTTP_CODEC, name, outboundHandlerAdapter);
        }
    }

    private void processOptions(Map<ChannelOption, Object> options, BiConsumer<ChannelOption, Object> biConsumer) {
        for (ChannelOption channelOption : options.keySet()) {
            String name = channelOption.name();
            Object value = options.get(channelOption);
            Optional<Field> declaredField = ReflectionUtils.findDeclaredField(ChannelOption.class, name);
            declaredField.ifPresent((field) -> {
                Optional<Class> typeArg = GenericTypeUtils.resolveGenericTypeArgument(field);
                typeArg.ifPresent((arg) -> {
                    Optional converted = environment.convert(value, arg);
                    converted.ifPresent((convertedValue) ->
                        biConsumer.accept(channelOption, convertedValue)
                    );
                });

            });
            if (!declaredField.isPresent()) {
                biConsumer.accept(channelOption, value);
            }
        }
    }

    @Override
    public void addChannel(Channel channel) {
        this.webSocketSessions.add(channel);
    }

    @Override
    public void removeChannel(Channel channel) {
        this.webSocketSessions.remove(channel);
    }

    @Override
    public ChannelGroup getChannelGroup() {
        return this.webSocketSessions;
    }

    /**
     *
     * @return {@link io.micronaut.http.server.netty.NettyHttpServer} which implements {@link WebSocketSessionRepository}
     */
    public WebSocketSessionRepository getWebSocketSessionRepository() {
        return this;
    }
}
