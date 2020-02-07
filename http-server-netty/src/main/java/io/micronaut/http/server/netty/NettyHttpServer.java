/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.event.ServiceShutdownEvent;
import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.stream.HttpStreamsServerHandler;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.decoders.HttpRequestDecoder;
import io.micronaut.http.server.netty.encoders.HttpResponseEncoder;
import io.micronaut.http.server.netty.ssl.HttpRequestCertificateHandler;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
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
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
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
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Implements the bootstrap and configuration logic for the Netty implementation of {@link EmbeddedServer}.
 *
 * @author Graeme Rocher
 * @see RoutingInBoundHandler
 * @since 1.0
 */
@Singleton
@Internal
public class NettyHttpServer implements EmbeddedServer, WebSocketSessionRepository {
    public static final String HTTP_STREAMS_CODEC = "http-streams-codec";
    public static final String HTTP_CHUNKED_HANDLER = "http-chunked-handler";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_CODEC = "http-codec";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_COMPRESSOR = "http-compressor";
    @SuppressWarnings("WeakerAccess")
    public static final String HTTP_DECOMPRESSOR = "http-decompressor";
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
    private final int specifiedPort;
    private final HttpCompressionStrategy httpCompressionStrategy;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private volatile int serverPort;
    private final ApplicationContext applicationContext;
    private final SslContext sslContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ChannelGroup webSocketSessions = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private EventLoopGroup workerGroup;
    private EventLoopGroup parentGroup;
    private EmbeddedServerInstance serviceInstance;
    private EventLoopGroupFactory eventLoopGroupFactory;

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
     * @param serverSslBuilder                        The Netty Server SSL builder
     * @param outboundHandlers                        The outbound handlers
     * @param eventLoopGroupFactory                   The EventLoopGroupFactory
     * @param httpCompressionStrategy                 The http compression strategy
     * @param httpContentProcessorResolver            The http content processor resolver
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
        @Nullable ServerSslBuilder serverSslBuilder,
        List<ChannelOutboundHandler> outboundHandlers,
        EventLoopGroupFactory eventLoopGroupFactory,
        HttpCompressionStrategy httpCompressionStrategy,
        HttpContentProcessorResolver httpContentProcessorResolver
    ) {
        this.httpCompressionStrategy = httpCompressionStrategy;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        Optional<File> location = serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir -> DiskFileUpload.baseDirectory = dir.getAbsolutePath());
        this.applicationContext = applicationContext;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.beanLocator = applicationContext;
        this.environment = applicationContext.getEnvironment();
        this.serverConfiguration = serverConfiguration;
        this.router = router;
        this.ioExecutor = ioExecutor;
        this.specifiedPort = getHttpPort(serverConfiguration);

        int port = specifiedPort;
        if (serverSslBuilder != null) {
            this.sslConfiguration = serverSslBuilder.getSslConfiguration();
            this.sslContext = serverSslBuilder.build().orElse(null);
            if (this.sslConfiguration.isEnabled()) {
                port = sslConfiguration.getPort();
            }
        } else {
            this.sslConfiguration = null;
            this.sslContext = null;
        }

        this.serverPort = getPortOrDefault(port);
        this.executorSelector = executorSelector;
        OrderUtil.sort(outboundHandlers);
        this.outboundHandlers = outboundHandlers;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.staticResourceResolver = resourceResolver;
        this.threadFactory = threadFactory;
        this.webSocketBeanRegistry = WebSocketBeanRegistry.forServer(applicationContext);
        this.eventLoopGroupFactory = eventLoopGroupFactory;
    }

    /**
     * Randomizes port if not set.
     * @param port current port value
     * @return random port number if the original value was -1
     */
    private int getPortOrDefault(int port) {
        return port == -1 ? SocketUtils.findAvailableTcpPort() : port;
    }

    /**
     * Get the configured http port otherwise will default the value depending on the env.
     * @param serverConfiguration configuration object for the server
     * @return http port
     */
    private int getHttpPort(NettyHttpServerConfiguration serverConfiguration) {
        Optional<Integer> configPort = serverConfiguration.getPort();
        if (configPort.isPresent()) {
            return configPort.get();
        } else {
            if (environment.getActiveNames().contains(Environment.TEST)) {
                return -1;
            } else {
                return HttpServerConfiguration.DEFAULT_PORT;
            }
        }
    }

    @Override
    public boolean isKeepAlive() {
        return false;
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
                .channel(eventLoopGroupFactory.serverSocketChannelClass())
                .childHandler(new EmbeddedServerChannelInitializer());

            Optional<String> host = serverConfiguration.getHost();

            serverPort = bindServerToHost(serverBootstrap, host.orElse(null), serverPort, new AtomicInteger(0));
            List<Integer> defaultPorts = new ArrayList<>(2);
            defaultPorts.add(serverPort);
            if (serverConfiguration.isDualProtocol()) {
                // By default we will bind ssl first and then bind http after.
                int httpPort = getPortOrDefault(getHttpPort(serverConfiguration));
                defaultPorts.add(httpPort);
                bindServerToHost(serverBootstrap, host.orElse(null), httpPort, new AtomicInteger(0));
            }
            final Set<Integer> exposedPorts = router.getExposedPorts();
            if (CollectionUtils.isNotEmpty(exposedPorts)) {
                router.applyDefaultPorts(defaultPorts);
                for (Integer exposedPort : exposedPorts) {
                    try {
                        if (host.isPresent()) {
                            serverBootstrap.bind(host.get(), exposedPort).sync();
                        } else {
                            serverBootstrap.bind(exposedPort).sync();
                        }
                    } catch (Throwable e) {
                        final boolean isBindError = e instanceof BindException;
                        if (LOG.isErrorEnabled()) {
                            if (isBindError) {
                                LOG.error("Unable to start server. Additional specified server port {} already in use.", exposedPort);
                            } else {
                                LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                            }
                        }
                        throw new ServerStartupException("Unable to start Micronaut server on port: " + serverPort, e);
                    }
                }
            }
            fireStartupEvents();
            running.set(true);
        }

        return this;
    }

    @Override
    public synchronized EmbeddedServer stop() {
        if (isRunning() && workerGroup != null) {
            if (running.compareAndSet(true, false)) {
                stopInternal();
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
        return serverConfiguration.getHost()
                    .orElseGet(() -> Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST));
    }

    @Override
    public String getScheme() {
        return (sslConfiguration != null && sslConfiguration.isEnabled()) ? "https" : "http";
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
    protected EventLoopGroup createParentEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getParent());
    }

    /**
     * @return The worker event loop group
     */
    @SuppressWarnings("WeakerAccess")
    protected EventLoopGroup createWorkerEventLoopGroup() {
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
    private int bindServerToHost(ServerBootstrap serverBootstrap, @Nullable String host, int port, AtomicInteger attempts) {
        boolean isRandomPort = specifiedPort == -1;
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        if (applicationName.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Binding {} server to {}:{}", applicationName.get(), host != null ? host : "*", port);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Binding server to {}:{}", host != null ? host : "*", port);
            }
        }

        try {
            if (host != null) {
                serverBootstrap.bind(host, port).sync();
            } else {
                serverBootstrap.bind(port).sync();
            }
            return port;
        } catch (Throwable e) {
            final boolean isBindError = e instanceof BindException;
            if (LOG.isErrorEnabled()) {
                if (isBindError) {
                    LOG.error("Unable to start server. Port already {} in use.", port);
                } else {
                    LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                }
            }
            int attemptCount = attempts.getAndIncrement();

            if (isRandomPort && attemptCount < 3) {
                port = SocketUtils.findAvailableTcpPort();
                return bindServerToHost(serverBootstrap, host, port, attempts);
            } else {
                stopInternal();
                throw new ServerStartupException("Unable to start Micronaut server on port: " + port, e);
            }
        }
    }

    private void fireStartupEvents() {
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        applicationContext.publishEvent(new ServerStartupEvent(this));
        applicationName.ifPresent(id -> {
            this.serviceInstance = applicationContext.createBean(NettyEmbeddedServerInstance.class, id, this);
            applicationContext.publishEvent(new ServiceStartedEvent(serviceInstance));
        });
    }

    private void logShutdownErrorIfNecessary(Future<?> future) {
        if (!future.isSuccess()) {
            if (LOG.isWarnEnabled()) {
                Throwable e = future.cause();
                LOG.warn("Error stopping Micronaut server: " + e.getMessage(), e);
            }
        }
    }

    private void stopInternal() {
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
            serverConfiguration.getMultipart().getLocation().ifPresent(dir -> DiskFileUpload.baseDirectory = null);
        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error stopping Micronaut server: " + e.getMessage(), e);
            }
        }
    }

    private EventLoopGroup newEventLoopGroup(NettyHttpServerConfiguration.EventLoopConfig config) {
        if (config != null) {
            Optional<ExecutorService> executorService = config.getExecutorName().flatMap(name -> beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name)));
            int threads = config.getNumOfThreads();
            Integer ioRatio = config.getIoRatio().orElse(null);
            return executorService.map(service ->
                eventLoopGroupFactory.createEventLoopGroup(threads, service, ioRatio)
            ).orElseGet(() -> {
                if (threadFactory != null) {
                    return eventLoopGroupFactory.createEventLoopGroup(threads, threadFactory, ioRatio);
                } else {
                    return eventLoopGroupFactory.createEventLoopGroup(threads, ioRatio);
                }
            });
        } else {
            if (threadFactory != null) {
                return eventLoopGroupFactory.createEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory, null);
            } else {
                return eventLoopGroupFactory.createEventLoopGroup(null);
            }
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

    /**
     * A ChannelHandler to be added to pipeline.
     */
    private static interface IChannelHandlerEntry {

        ChannelPipeline addLast(ChannelPipeline pipeline, boolean ssl);
    }

    /**
     * A Sharable ChannelHandler to be added to pipeline.
     */
    private static class SharableChannelHandlerEntry implements IChannelHandlerEntry {
        final String name;
        final ChannelHandler channelHandler;

        public SharableChannelHandlerEntry(ChannelHandler channelHandler) {
            this(null, channelHandler);
        }

        public SharableChannelHandlerEntry(String name, ChannelHandler channelHandler) {
            this.name = name;
            this.channelHandler = channelHandler;
        }

        @Override
        public ChannelPipeline addLast(ChannelPipeline pipeline, boolean ssl) {
            if (name == null) {
                pipeline.addLast(channelHandler);
            } else {
                pipeline.addLast(name, channelHandler);
            }
            return pipeline;
        }

    }

    /**
     * A Non Sharable ChannelHandler to be added to pipeline.
     */
    private static class NonSharableChannelHandlerEntry implements IChannelHandlerEntry {
        final String name;
        final Supplier<ChannelHandler> channelHandler;

        public NonSharableChannelHandlerEntry(Supplier<ChannelHandler> channelHandler) {
            this(null, channelHandler);
        }

        public NonSharableChannelHandlerEntry(String name, Supplier<ChannelHandler> channelHandler) {
            this.name = name;
            this.channelHandler = channelHandler;
        }

        @Override
        public ChannelPipeline addLast(ChannelPipeline pipeline, boolean ssl) {
            if (name == null) {
                pipeline.addLast(channelHandler.get());
            } else {
                pipeline.addLast(name, channelHandler.get());
            }
            return pipeline;
        }
    }

    /**
     * A Sharable ssl related ChannelHandler to be added to pipeline.
     */
    private static class SslChannelHandlerEntry extends SharableChannelHandlerEntry {

        public SslChannelHandlerEntry(ChannelHandler channelHandler) {
            super(channelHandler);
        }

        @Override
        public ChannelPipeline addLast(ChannelPipeline pipeline, boolean ssl) {
            if (ssl) {
                return super.addLast(pipeline, ssl);
            }
            return pipeline;
        }
    }

    /**
     * A ssl ChannelHandler to be added to pipeline.
     */
    private static class SslContextChannelHandlerEntry implements IChannelHandlerEntry {
        final SslContext sslContext;

        public SslContextChannelHandlerEntry(SslContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public ChannelPipeline addLast(ChannelPipeline pipeline, boolean ssl) {
            if (ssl) {
                pipeline.addLast(sslContext.newHandler(pipeline.channel().alloc()));
            }
            return pipeline;
        }
    }

    /**
     * The embedded server channel initializer.
     */
    private class EmbeddedServerChannelInitializer extends ChannelInitializer {
        private final List<IChannelHandlerEntry> channelHandlers;
        private final boolean ssl = sslContext != null && sslConfiguration != null;

        public EmbeddedServerChannelInitializer() {
            channelHandlers = new ArrayList<>(15);

            final HttpRequestDecoder requestDecoder = new HttpRequestDecoder(NettyHttpServer.this, environment, serverConfiguration);
            HttpRequestCertificateHandler requestCertificateHandler = null;
            final HttpResponseEncoder responseDecoder = new HttpResponseEncoder(mediaTypeCodecRegistry, serverConfiguration);
            final RoutingInBoundHandler routingHandler = new RoutingInBoundHandler(applicationContext, router, mediaTypeCodecRegistry,
                    customizableResponseTypeHandlerRegistry, staticResourceResolver, serverConfiguration, requestArgumentSatisfier, executorSelector, ioExecutor,
                    httpContentProcessorResolver);

            if (ssl) {
                requestCertificateHandler = new HttpRequestCertificateHandler();
                channelHandlers.add(new SslContextChannelHandlerEntry(sslContext));
            }
            if (serverConfiguration.getLogLevel().isPresent()) {
                channelHandlers.add(new NonSharableChannelHandlerEntry(() -> new LoggingHandler(serverConfiguration.getLogLevel().get())));
            }

            final Duration idleTime = serverConfiguration.getIdleTimeout();
            if (!idleTime.isNegative()) {
                channelHandlers.add(new NonSharableChannelHandlerEntry(() -> new IdleStateHandler((int) serverConfiguration.getReadIdleTimeout().getSeconds(),
                        (int) serverConfiguration.getWriteIdleTimeout().getSeconds(), (int) idleTime.getSeconds())));
            }

            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_CODEC,
                    () -> new HttpServerCodec(serverConfiguration.getMaxInitialLineLength(), serverConfiguration.getMaxHeaderSize(),
                            serverConfiguration.getMaxChunkSize(), serverConfiguration.isValidateHeaders(), serverConfiguration.getInitialBufferSize())));

            int i = 0;
            for (ChannelHandler outboundHandlerAdapter : outboundHandlers) {
                String name;
                if (outboundHandlerAdapter instanceof Named) {
                    name = ((Named) outboundHandlerAdapter).getName();
                } else {
                    name = NettyHttpServer.MICRONAUT_HANDLER + NettyHttpServer.OUTBOUND_KEY + ++i;
                }
                channelHandlers.add(new SharableChannelHandlerEntry(name, outboundHandlerAdapter));
            }

            channelHandlers.add(new NonSharableChannelHandlerEntry(FlowControlHandler::new));
            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_KEEP_ALIVE_HANDLER, HttpServerKeepAliveHandler::new));
            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_COMPRESSOR, () -> new SmartHttpContentCompressor(httpCompressionStrategy)));
            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_DECOMPRESSOR, HttpContentDecompressor::new));
            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_STREAMS_CODEC, HttpStreamsServerHandler::new));
            channelHandlers.add(new NonSharableChannelHandlerEntry(HTTP_CHUNKED_HANDLER, ChunkedWriteHandler::new));
            channelHandlers.add(new SharableChannelHandlerEntry(HttpRequestDecoder.ID, requestDecoder));
            if (ssl) {
                channelHandlers.add(new SslChannelHandlerEntry(requestCertificateHandler));
            }
            channelHandlers.add(new SharableChannelHandlerEntry(HttpResponseEncoder.ID, responseDecoder));
            channelHandlers.add(
                    new NonSharableChannelHandlerEntry(NettyServerWebSocketUpgradeHandler.ID, () -> new NettyServerWebSocketUpgradeHandler(getWebSocketSessionRepository(),
                            router, requestArgumentSatisfier.getBinderRegistry(), webSocketBeanRegistry, mediaTypeCodecRegistry, applicationContext)));
            channelHandlers.add(new SharableChannelHandlerEntry(MICRONAUT_HANDLER, routingHandler));
            ((ArrayList<IChannelHandlerEntry>) channelHandlers).trimToSize();
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            final boolean sslFlag = ssl && ((InetSocketAddress) ch.localAddress()).getPort() == sslConfiguration.getPort();
            for (IChannelHandlerEntry entry: channelHandlers) {
                entry.addLast(pipeline, sslFlag);
            }
        }

    }

}
