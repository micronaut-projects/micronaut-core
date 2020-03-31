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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.channel.*;
import io.micronaut.http.netty.stream.HttpStreamsServerHandler;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http2.*;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.File;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.*;
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
    public static final String HTTP2_HANDLER = "http2-handler";
    public static final String FLOW_CONTROL_HANDLER = "flow-control-handler";

    @SuppressWarnings("WeakerAccess")
    public static final String OUTBOUND_KEY = "-outbound-";

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private final List<ChannelOutboundHandler> outboundHandlers;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
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
    private final EventLoopGroupRegistry eventLoopGroupRegistry;
    private final HttpVersion httpVersion;
    private final HttpRequestCertificateHandler requestCertificateHandler;
    private final RoutingInBoundHandler routingHandler;
    private volatile int serverPort;
    private final ApplicationContext applicationContext;
    private final SslContext sslContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ChannelGroup webSocketSessions = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private boolean shutdownWorker = false;
    private boolean shutdownParent = false;
    private EventLoopGroup workerGroup;
    private EventLoopGroup parentGroup;
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
     * @param serverSslBuilder                        The Netty Server SSL builder
     * @param outboundHandlers                        The outbound handlers
     * @param eventLoopGroupRegistry                  The event loop registry
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
            @javax.inject.Named(TaskExecutors.IO) Provider<ExecutorService> ioExecutor,
            @javax.inject.Named(NettyThreadFactory.NAME) ThreadFactory threadFactory,
            ExecutorSelector executorSelector,
            @Nullable ServerSslBuilder serverSslBuilder,
            List<ChannelOutboundHandler> outboundHandlers,
            EventLoopGroupFactory eventLoopGroupFactory,
            EventLoopGroupRegistry eventLoopGroupRegistry,
            HttpCompressionStrategy httpCompressionStrategy,
            HttpContentProcessorResolver httpContentProcessorResolver
    ) {
        this.httpCompressionStrategy = httpCompressionStrategy;
        Optional<File> location = serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir -> DiskFileUpload.baseDirectory = dir.getAbsolutePath());
        this.applicationContext = applicationContext;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.beanLocator = applicationContext;
        this.environment = applicationContext.getEnvironment();
        this.serverConfiguration = serverConfiguration;
        this.router = router;
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

        this.httpVersion = serverConfiguration.getHttpVersion();
        this.serverPort = getPortOrDefault(port);
        OrderUtil.sort(outboundHandlers);
        this.outboundHandlers = outboundHandlers;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.staticResourceResolver = resourceResolver;
        this.threadFactory = threadFactory;
        this.webSocketBeanRegistry = WebSocketBeanRegistry.forServer(applicationContext);
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.eventLoopGroupRegistry = eventLoopGroupRegistry;
        this.requestCertificateHandler = new HttpRequestCertificateHandler();
        this.routingHandler = new RoutingInBoundHandler(
                applicationContext,
                router,
                mediaTypeCodecRegistry,
                customizableResponseTypeHandlerRegistry,
                staticResourceResolver,
                serverConfiguration,
                requestArgumentSatisfier,
                executorSelector,
                SupplierUtil.memoized(ioExecutor::get),
                httpContentProcessorResolver
        );
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
        Integer configPort = serverConfiguration.getPort().orElse(null);
        return getHttpPort(configPort);
    }

    private int getHttpPort(Integer configPort) {
        if (configPort != null) {
            return configPort;
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
            EventLoopGroupConfiguration workerConfig = resolveWorkerConfiguration();
            workerGroup = createWorkerEventLoopGroup(workerConfig);
            parentGroup = createParentEventLoopGroup();
            ServerBootstrap serverBootstrap = createServerBootstrap();

            processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
            processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);
            serverBootstrap = serverBootstrap.group(parentGroup, workerGroup)
                .channel(eventLoopGroupFactory.serverSocketChannelClass(workerConfig))
                .childHandler(new NettyHttpServerInitializer());

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

    private EventLoopGroupConfiguration resolveWorkerConfiguration() {
        EventLoopGroupConfiguration workerConfig = serverConfiguration.getWorker();
        if (workerConfig == null) {
            workerConfig = eventLoopGroupRegistry.getEventLoopGroupConfiguration(EventLoopGroupConfiguration.DEFAULT).orElse(null);
        } else {
            final String eventLoopGroupName = workerConfig.getName();
            if (!EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroupName)) {
                workerConfig = eventLoopGroupRegistry.getEventLoopGroupConfiguration(eventLoopGroupName).orElse(workerConfig);
            }
        }
        return workerConfig;
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
        return (sslConfiguration != null && sslConfiguration.isEnabled()) ? io.micronaut.http.HttpRequest.SCHEME_HTTPS : io.micronaut.http.HttpRequest.SCHEME_HTTP;
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
        final NettyHttpServerConfiguration.Parent parent = serverConfiguration.getParent();
        return eventLoopGroupRegistry.getEventLoopGroup(parent != null ? parent.getName() : NettyHttpServerConfiguration.Parent.NAME)
                .orElseGet(() -> {
                    final EventLoopGroup newGroup = newEventLoopGroup(parent);
                    shutdownParent = true;
                    return newGroup;
                });
    }

    /**
     * @return The worker event loop group
     * @param workerConfig The worker configuration
     */
    @SuppressWarnings("WeakerAccess")
    protected EventLoopGroup createWorkerEventLoopGroup(@Nullable EventLoopGroupConfiguration workerConfig) {
        return eventLoopGroupRegistry.getEventLoopGroup(workerConfig != null ? workerConfig.getName() : EventLoopGroupConfiguration.DEFAULT)
                .orElseGet(() -> {
                    final EventLoopGroup newGroup = newEventLoopGroup(workerConfig);
                    shutdownWorker = true;
                    return newGroup;
                });
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
            applicationContext.publishEvent(new ServiceReadyEvent(serviceInstance));
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
            if (shutdownParent) {
                parentGroup.shutdownGracefully()
                        .addListener(this::logShutdownErrorIfNecessary);
            }
            if (shutdownWorker) {
                workerGroup.shutdownGracefully()
                        .addListener(this::logShutdownErrorIfNecessary);
            }
            webSocketSessions.close();
            applicationContext.publishEvent(new ServerShutdownEvent(this));
            if (serviceInstance != null) {
                applicationContext.publishEvent(new ServiceStoppedEvent(serviceInstance));
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

    private EventLoopGroup newEventLoopGroup(EventLoopGroupConfiguration config) {
        if (config != null) {
            ExecutorService executorService = config.getExecutorName()
                    .flatMap(name -> beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name))).orElse(null);
            if (executorService != null) {
                return eventLoopGroupFactory.createEventLoopGroup(
                        config.getNumThreads(),
                        executorService,
                        config.getIoRatio().orElse(null)
                );
            } else {
                return eventLoopGroupFactory.createEventLoopGroup(
                        config,
                        threadFactory
                );
            }
        } else {
            return eventLoopGroupFactory.createEventLoopGroup(
                    new DefaultEventLoopGroupConfiguration(), threadFactory
            );
        }
    }

    private void registerMicronautChannelHandlers(Map<String, ChannelHandler> channelHandlerMap) {
        int i = 0;
        for (ChannelHandler outboundHandlerAdapter : outboundHandlers) {
            String name;
            if (outboundHandlerAdapter instanceof Named) {
                name = ((Named) outboundHandlerAdapter).getName();
            } else {
                name = NettyHttpServer.MICRONAUT_HANDLER + NettyHttpServer.OUTBOUND_KEY + ++i;
            }
            channelHandlerMap.put(name, outboundHandlerAdapter);
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

    private HttpToHttp2ConnectionHandler newHttpToHttp2ConnectionHandler() {
        Http2Connection connection = new DefaultHttp2Connection(true);
        final Http2FrameListener http2ToHttpAdapter = new StreamingInboundHttp2ToHttpAdapter(
                connection,
                (int) serverConfiguration.getMaxRequestSize(),
                serverConfiguration.isValidateHeaders(),
                true
        );

        final HttpToHttp2ConnectionHandlerBuilder builder = new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(http2ToHttpAdapter);

        serverConfiguration.getLogLevel().ifPresent(logLevel ->
                builder.frameLogger(new Http2FrameLogger(logLevel, NettyHttpServer.class))
        );
        return builder.connection(connection).build();
    }

    /**
     * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once decided, the Netty
     * pipeline is setup with the correct handlers for the selected protocol.
     */
    private final class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

        private final SslContext sslContext;

        /**
         * Default constructor.
         * @param sslContext The SSL context
         * @param fallbackProtocol The fallback protocol
         */
        Http2OrHttpHandler(SslContext sslContext, String fallbackProtocol) {
            super(fallbackProtocol);
            this.sslContext = sslContext;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                if (!event.isSuccess()) {
                    final Throwable cause = event.cause();
                    if (!(cause instanceof ClosedChannelException)) {
                        super.userEventTriggered(ctx, evt);
                    } else {
                        return;
                    }
                }
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            final ChannelPipeline pipeline = ctx.pipeline();
            configurePipeline(protocol, pipeline);
            ctx.read();
        }

        private void configurePipeline(String protocol, ChannelPipeline pipeline) {
            Map<String, ChannelHandler> handlers = getHandlerForProtocol(protocol);
            handlers.forEach(pipeline::addLast);
        }

        @NotNull
        private Map<String, ChannelHandler> getHandlerForProtocol(@Nullable String protocol) {
            final HttpRequestDecoder requestDecoder = new HttpRequestDecoder(NettyHttpServer.this, environment, serverConfiguration);
            final HttpResponseEncoder responseDecoder = new HttpResponseEncoder(mediaTypeCodecRegistry, serverConfiguration);
            final Duration idleTime = serverConfiguration.getIdleTimeout();
            Map<String, ChannelHandler> handlers = new LinkedHashMap<>(15);
            if (!idleTime.isNegative()) {
                handlers.put("idle-state-handler", new IdleStateHandler(
                        (int) serverConfiguration.getReadIdleTimeout().getSeconds(),
                        (int) serverConfiguration.getWriteIdleTimeout().getSeconds(),
                        (int) idleTime.getSeconds()));
            }
            if (protocol == null) {
                handlers.put(FLOW_CONTROL_HANDLER, new FlowControlHandler());
            } else if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                final HttpToHttp2ConnectionHandler httpToHttp2ConnectionHandler = newHttpToHttp2ConnectionHandler();
                handlers.put(HTTP2_HANDLER, httpToHttp2ConnectionHandler);
                registerMicronautChannelHandlers(handlers);
                handlers.put(FLOW_CONTROL_HANDLER, new FlowControlHandler());
            } else {
                handlers.put(HTTP_CODEC, new HttpServerCodec(
                        serverConfiguration.getMaxInitialLineLength(),
                        serverConfiguration.getMaxHeaderSize(),
                        serverConfiguration.getMaxChunkSize(),
                        serverConfiguration.isValidateHeaders(),
                        serverConfiguration.getInitialBufferSize()
                ));
                registerMicronautChannelHandlers(handlers);
                handlers.put(FLOW_CONTROL_HANDLER, new FlowControlHandler());
                handlers.put(HTTP_KEEP_ALIVE_HANDLER, new HttpServerKeepAliveHandler());
                handlers.put(HTTP_COMPRESSOR, new SmartHttpContentCompressor(httpCompressionStrategy));
                handlers.put(HTTP_DECOMPRESSOR, new HttpContentDecompressor());
            }

            handlers.put(HTTP_STREAMS_CODEC, new HttpStreamsServerHandler());
            handlers.put(HTTP_CHUNKED_HANDLER, new ChunkedWriteHandler());
            handlers.put(HttpRequestDecoder.ID, requestDecoder);
            if (sslContext != null) {
                handlers.put("request-certificate-handler", requestCertificateHandler);
            }
            handlers.put(HttpResponseEncoder.ID, responseDecoder);
            handlers.put(NettyServerWebSocketUpgradeHandler.ID, new NettyServerWebSocketUpgradeHandler(
                    getWebSocketSessionRepository(),
                    router,
                    requestArgumentSatisfier.getBinderRegistry(),
                    webSocketBeanRegistry,
                    mediaTypeCodecRegistry,
                    applicationContext
            ));
            handlers.put(MICRONAUT_HANDLER, routingHandler);
            return handlers;
        }
    }

    /**
     * An HTTP server initializer for Netty.
     */
    private class NettyHttpServerInitializer extends ChannelInitializer<SocketChannel> {

        final LoggingHandler loggingHandler =
                serverConfiguration.getLogLevel().isPresent() ? new LoggingHandler(NettyHttpServer.class, serverConfiguration.getLogLevel().get()) : null;

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();

            int port = ch.localAddress().getPort();
            boolean ssl = sslContext != null && sslConfiguration != null && port == serverPort;
            if (ssl) {
                pipeline.addLast(sslContext.newHandler(ch.alloc()));
            }

            if (loggingHandler != null) {
                pipeline.addLast(loggingHandler);
            }

            if (httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
                new Http2OrHttpHandler(sslContext, serverConfiguration.getFallbackProtocol())
                        .configurePipeline(ApplicationProtocolNames.HTTP_1_1, pipeline);
            } else {
                final Http2OrHttpHandler http2OrHttpHandler = new Http2OrHttpHandler(sslContext, serverConfiguration.getFallbackProtocol());
                if (ssl) {
                    pipeline.addLast(http2OrHttpHandler);
                } else {
                    final HttpToHttp2ConnectionHandler connectionHandler = newHttpToHttp2ConnectionHandler();
                    final String fallbackHandlerName = "http1-fallback-handler";
                    HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
                        if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {

                            return new Http2ServerUpgradeCodec(HTTP2_HANDLER, connectionHandler) {
                                @Override
                                public void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest) {
                                    final ChannelPipeline p = ctx.pipeline();
                                    p.remove(fallbackHandlerName);
                                    http2OrHttpHandler.getHandlerForProtocol(null)
                                            .forEach(p::addLast);
                                    super.upgradeTo(ctx, upgradeRequest);
                                }
                            };
                        } else {
                            return null;
                        }
                    };
                    final HttpServerCodec sourceCodec = new HttpServerCodec();
                    final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(
                            sourceCodec,
                            upgradeCodecFactory
                    );
                    final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                            new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, connectionHandler);

                    pipeline.addLast(cleartextHttp2ServerUpgradeHandler);

                    pipeline.addLast(fallbackHandlerName, new SimpleChannelInboundHandler<HttpMessage>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) {
                            // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                            if (msg instanceof HttpRequest) {
                                HttpRequest req = (HttpRequest) msg;
                                if (req.headers().contains(AbstractNettyHttpRequest.STREAM_ID)) {
                                    ChannelPipeline pipeline = ctx.pipeline();
                                    pipeline.remove(this);
                                    pipeline.fireChannelRead(ReferenceCountUtil.retain(msg));
                                    return;
                                }
                            }
                            ChannelPipeline pipeline = ctx.pipeline();
                            final HttpServerCodec upgradeCodec = pipeline.get(HttpServerCodec.class);
                            pipeline.remove(upgradeCodec);
                            pipeline.remove(upgradeHandler);
                            pipeline.remove(this);
                            // reconfigure for http1
                            http2OrHttpHandler.getHandlerForProtocol(ApplicationProtocolNames.HTTP_1_1)
                                    .forEach(pipeline::addLast);
                            pipeline.fireChannelRead(ReferenceCountUtil.retain(msg));
                        }
                    });

                }
            }
        }
    }
}
