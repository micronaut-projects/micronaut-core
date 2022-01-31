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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.discovery.EmbeddedServerInstance;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.ssl.HttpRequestCertificateHandler;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.http.server.util.DefaultHttpHostResolver;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.Router;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements the bootstrap and configuration logic for the Netty implementation of {@link io.micronaut.runtime.server.EmbeddedServer}.
 *
 * @author Graeme Rocher
 * @see RoutingInBoundHandler
 * @since 1.0
 */
@Internal
@TypeHint(
        value = ChannelOption.class,
        accessType = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS}
)
public class NettyHttpServer implements NettyEmbeddedServer {

    @SuppressWarnings("WeakerAccess")
    public static final String OUTBOUND_KEY = "-outbound-";

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private final NettyEmbeddedServices nettyEmbeddedServices;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final ServerSslConfiguration sslConfiguration;
    private final Environment environment;
    private final int specifiedPort;
    private final RoutingInBoundHandler routingHandler;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final boolean isDefault;
    private volatile int serverPort;
    private final ApplicationContext applicationContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ChannelGroup webSocketSessions = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final HttpHostResolver hostResolver;
    private boolean shutdownWorker = false;
    private boolean shutdownParent = false;
    private EventLoopGroup workerGroup;
    private EventLoopGroup parentGroup;
    private EmbeddedServerInstance serviceInstance;
    private final Collection<ChannelPipelineListener> pipelineListeners = new ArrayList<>(2);
    private NettyHttpServerInitializer childHandler;
    private final Set<Integer> boundPorts = new HashSet<>(2);

    /**
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param nettyEmbeddedServices                   The embedded server context
     * @param handlerRegistry                         The handler registry
     * @param isDefault                               Is this the default server
     */
    @SuppressWarnings("ParameterNumber")
    public NettyHttpServer(
            NettyHttpServerConfiguration serverConfiguration,
            NettyEmbeddedServices nettyEmbeddedServices,
            NettyCustomizableResponseTypeHandlerRegistry handlerRegistry,
            boolean isDefault) {
        this.isDefault = isDefault;
        this.serverConfiguration = serverConfiguration;
        this.nettyEmbeddedServices = nettyEmbeddedServices;
        Optional<File> location = this.serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir -> DiskFileUpload.baseDirectory = dir.getAbsolutePath());
        this.applicationContext = nettyEmbeddedServices.getApplicationContext();
        this.environment = applicationContext.getEnvironment();

        int port;
        final ServerSslBuilder serverSslBuilder = nettyEmbeddedServices.getServerSslBuilder();
        if (serverSslBuilder != null) {
            this.sslConfiguration = serverSslBuilder.getSslConfiguration();
            if (this.sslConfiguration.isEnabled()) {
                port = sslConfiguration.getPort();
                this.specifiedPort = port;
            } else {
                port = getHttpPort(this.serverConfiguration);
                this.specifiedPort = port;
            }
        } else {
            port = getHttpPort(this.serverConfiguration);
            this.specifiedPort = port;
            this.sslConfiguration = null;
        }

        this.serverPort = port;
        ApplicationEventPublisher<HttpRequestTerminatedEvent> httpRequestTerminatedEventPublisher = nettyEmbeddedServices
                .getEventPublisher(HttpRequestTerminatedEvent.class);
        final Supplier<ExecutorService> ioExecutor = SupplierUtil.memoized(() ->
                 nettyEmbeddedServices.getExecutorSelector()
                         .select(TaskExecutors.IO).orElse(null)
        );
        this.httpContentProcessorResolver = new DefaultHttpContentProcessorResolver(
                nettyEmbeddedServices.getApplicationContext(),
                () -> serverConfiguration
        );
        this.routingHandler = new RoutingInBoundHandler(
                serverConfiguration,
                handlerRegistry,
                nettyEmbeddedServices,
                ioExecutor,
                httpContentProcessorResolver,
                httpRequestTerminatedEventPublisher
        );
        this.hostResolver = new DefaultHttpHostResolver(serverConfiguration, () -> NettyHttpServer.this);
    }

    /**
     * Get the configured http port otherwise will default the value depending on the env.
     *
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
        return running.get();
    }

    @Override
    public synchronized NettyEmbeddedServer start() {
        if (!isRunning()) {
            //suppress unused
            //done here to prevent a blocking service loader in the event loop
            EventLoopGroupConfiguration workerConfig = resolveWorkerConfiguration();
            workerGroup = createWorkerEventLoopGroup(workerConfig);
            parentGroup = createParentEventLoopGroup();
            ServerBootstrap serverBootstrap = createServerBootstrap();
            serverBootstrap.channelFactory(() ->
               nettyEmbeddedServices.getServerSocketChannelInstance(workerConfig)
            );

            processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
            processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);
            childHandler = new NettyHttpServerInitializer();
            serverBootstrap = serverBootstrap.group(parentGroup, workerGroup)
                    .childHandler(childHandler);

            Optional<String> host = serverConfiguration.getHost();
            final String definedHost = host.orElse(null);
            serverPort = bindServerToHost(serverBootstrap, definedHost, serverPort);
            if (isDefault) {
                List<Integer> defaultPorts = new ArrayList<>(2);
                defaultPorts.add(serverPort);
                if (serverConfiguration.isDualProtocol()) {
                    defaultPorts.add(
                            bindServerToHost(serverBootstrap, definedHost, getHttpPort(serverConfiguration))
                    );
                }
                final Router router = this.nettyEmbeddedServices.getRouter();
                final Set<Integer> exposedPorts = router.getExposedPorts();
                this.boundPorts.addAll(defaultPorts);
                if (CollectionUtils.isNotEmpty(exposedPorts)) {
                    router.applyDefaultPorts(defaultPorts);
                    for (Integer exposedPort : exposedPorts) {
                        if (!defaultPorts.contains(exposedPort)) {
                            try {
                                if (definedHost != null) {
                                    serverBootstrap.bind(definedHost, exposedPort).sync();
                                } else {
                                    serverBootstrap.bind(exposedPort).sync();
                                }
                                this.boundPorts.add(exposedPort);
                            } catch (Throwable e) {
                                final boolean isBindError = e instanceof BindException;
                                if (LOG.isErrorEnabled()) {
                                    if (isBindError) {
                                        LOG.error("Unable to start server. Additional specified server port {} already in use.",
                                                  exposedPort);
                                    } else {
                                        LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                                    }
                                }
                                throw new ServerStartupException("Unable to start Micronaut server on port: " + serverPort, e);
                            }
                        }
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
            workerConfig = nettyEmbeddedServices.getEventLoopGroupRegistry()
                    .getEventLoopGroupConfiguration(EventLoopGroupConfiguration.DEFAULT).orElse(null);
        } else {
            final String eventLoopGroupName = workerConfig.getName();
            if (!EventLoopGroupConfiguration.DEFAULT.equals(eventLoopGroupName)) {
                workerConfig = nettyEmbeddedServices.getEventLoopGroupRegistry()
                        .getEventLoopGroupConfiguration(eventLoopGroupName).orElse(workerConfig);
            }
        }
        return workerConfig;
    }

    @Override
    public synchronized NettyEmbeddedServer stop() {
        if (isRunning() && workerGroup != null) {
            if (running.compareAndSet(true, false)) {
                stopInternal();
            }
        }
        return this;
    }

    @Override
    public int getPort() {
        if (!isRunning() && serverPort == -1) {
            throw new UnsupportedOperationException("Retrieving the port from the server before it has started is not supported when binding to a random port");
        }
        return serverPort;
    }

    @Override
    public String getHost() {
        return serverConfiguration.getHost()
                .orElseGet(() -> Optional.ofNullable(CachedEnvironment.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST));
    }

    @Override
    public String getScheme() {
        return (sslConfiguration != null && sslConfiguration.isEnabled())
                ? io.micronaut.http.HttpRequest.SCHEME_HTTPS
                : io.micronaut.http.HttpRequest.SCHEME_HTTP;
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

    @Override
    public final Set<Integer> getBoundPorts() {
        return Collections.unmodifiableSet(this.boundPorts);
    }

    /**
     * @return The parent event loop group
     */
    @SuppressWarnings("WeakerAccess")
    protected EventLoopGroup createParentEventLoopGroup() {
        final NettyHttpServerConfiguration.Parent parent = serverConfiguration.getParent();
        return nettyEmbeddedServices.getEventLoopGroupRegistry()
                .getEventLoopGroup(parent != null ? parent.getName() : NettyHttpServerConfiguration.Parent.NAME)
                .orElseGet(() -> {
                    final EventLoopGroup newGroup = newEventLoopGroup(parent);
                    shutdownParent = true;
                    return newGroup;
                });
    }

    /**
     * @param workerConfig The worker configuration
     * @return The worker event loop group
     */
    @SuppressWarnings("WeakerAccess")
    protected EventLoopGroup createWorkerEventLoopGroup(@Nullable EventLoopGroupConfiguration workerConfig) {
        String configName = workerConfig != null ? workerConfig.getName() : EventLoopGroupConfiguration.DEFAULT;
        return nettyEmbeddedServices.getEventLoopGroupRegistry().getEventLoopGroup(configName)
                .orElseGet(() -> {
                    LOG.warn("The configuration for 'micronaut.server.netty.worker.{}' is deprecated. " +
                                     "Use 'micronaut.netty.event-loops.default' configuration instead.", configName);
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
    private int bindServerToHost(ServerBootstrap serverBootstrap, @Nullable String host, int port) {
        boolean isRandomPort = specifiedPort == -1;
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        if (applicationName.isPresent()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Binding {} server to {}:{}", applicationName.get(), host != null ? host : "*", port);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Binding server to {}:{}", host != null ? host : "*", port);
            }
        }

        try {
            if (isRandomPort) {
                // bind to zero to get a random port
                final ChannelFuture future;
                if (host != null) {
                    future = serverBootstrap.bind(host, 0).sync();
                } else {
                    future = serverBootstrap.bind(0).sync();
                }
                InetSocketAddress ia = (InetSocketAddress) future.channel().localAddress();
                return ia.getPort();
            } else {
                if (host != null) {
                    serverBootstrap.bind(host, port).sync();
                } else {
                    serverBootstrap.bind(port).sync();
                }
            }
            return port;
        } catch (Throwable e) {
            final boolean isBindError = e instanceof BindException;
            if (LOG.isErrorEnabled()) {
                if (isBindError) {
                    LOG.error("Unable to start server. Port {} already in use.", port);
                } else {
                    LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                }
            }
            stopInternal();
            throw new ServerStartupException("Unable to start Micronaut server on port: " + port, e);
        }
    }

    private void fireStartupEvents() {
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        applicationContext.getEventPublisher(ServerStartupEvent.class)
                .publishEvent(new ServerStartupEvent(this));
        applicationName.ifPresent(id -> {
            this.serviceInstance = applicationContext.createBean(NettyEmbeddedServerInstance.class, id, this);
            applicationContext
                    .getEventPublisher(ServiceReadyEvent.class)
                    .publishEvent(new ServiceReadyEvent(serviceInstance));
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
                EventLoopGroupConfiguration parent = serverConfiguration.getParent();
                if (parent != null) {
                    long quietPeriod = parent.getShutdownQuietPeriod().toMillis();
                    long timeout = parent.getShutdownTimeout().toMillis();
                    parentGroup.shutdownGracefully(quietPeriod, timeout, TimeUnit.MILLISECONDS)
                            .addListener(this::logShutdownErrorIfNecessary);
                } else {
                    parentGroup.shutdownGracefully()
                            .addListener(this::logShutdownErrorIfNecessary);
                }
            }
            if (shutdownWorker) {
                workerGroup.shutdownGracefully()
                        .addListener(this::logShutdownErrorIfNecessary);
            }
            webSocketSessions.close();
            applicationContext.getEventPublisher(ServerShutdownEvent.class).publishEvent(new ServerShutdownEvent(this));
            if (serviceInstance != null) {
                applicationContext.getEventPublisher(ServiceStoppedEvent.class)
                        .publishEvent(new ServiceStoppedEvent(serviceInstance));
            }
            if (isDefault) {
                if (applicationContext.isRunning()) {
                    applicationContext.stop();
                }
                serverConfiguration.getMultipart().getLocation().ifPresent(dir -> DiskFileUpload.baseDirectory = null);
            }
            serverConfiguration.getMultipart().getLocation().ifPresent(dir -> DiskFileUpload.baseDirectory = null);
            childHandler = null;
            this.boundPorts.clear();
        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error stopping Micronaut server: " + e.getMessage(), e);
            }
        }
    }

    private EventLoopGroup newEventLoopGroup(EventLoopGroupConfiguration config) {
        if (config != null) {
            ExecutorService executorService = config.getExecutorName()
                    .flatMap(name -> applicationContext.findBean(ExecutorService.class, Qualifiers.byName(name))).orElse(null);
            if (executorService != null) {
                return nettyEmbeddedServices.createEventLoopGroup(
                        config.getNumThreads(),
                        executorService,
                        config.getIoRatio().orElse(null)
                );
            } else {
                return nettyEmbeddedServices.createEventLoopGroup(
                        config
                );
            }
        } else {
            return nettyEmbeddedServices.createEventLoopGroup(
                    new DefaultEventLoopGroupConfiguration()
            );
        }
    }

    private void processOptions(Map<ChannelOption, Object> options, BiConsumer<ChannelOption, Object> biConsumer) {
        final ChannelOptionFactory channelOptionFactory = nettyEmbeddedServices.getChannelOptionFactory();
        options.forEach((option, value) -> biConsumer.accept(option,
                                                             channelOptionFactory.convertValue(option, value, environment)));
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
                .frameListener(http2ToHttpAdapter)
                .validateHeaders(serverConfiguration.isValidateHeaders())
                .initialSettings(serverConfiguration.getHttp2().http2Settings());

        serverConfiguration.getLogLevel().ifPresent(logLevel ->
                                                            builder.frameLogger(new Http2FrameLogger(logLevel,
                                                                                                     NettyHttpServer.class))
        );
        return builder.connection(connection).build();
    }

    @Override
    public boolean isClientChannel() {
        return false;
    }

    @Override
    public void doOnConnect(@NonNull ChannelPipelineListener listener) {
        this.pipelineListeners.add(Objects.requireNonNull(listener, "The listener cannot be null"));
    }

    @Override
    public Set<String> getObservedConfigurationPrefixes() {
        return Collections.singleton(HttpServerConfiguration.PREFIX);
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        // if anything under HttpServerConfiguration.PREFIX changes re-build
        // the NettyHttpServerInitializer in the server bootstrap to apply changes
        // this will ensure re-configuration to HTTPS settings, read-timeouts, logging etc. apply
        // configuration properties are auto-refreshed so will be visible automatically
        if (childHandler != null) {
            childHandler.initHttpCoders();
        }
    }

    void triggerPipelineListeners(ChannelPipeline pipeline) {
        for (ChannelPipelineListener pipelineListener : pipelineListeners) {
            pipelineListener.onConnect(pipeline);
        }
    }

    static Predicate<String> inclusionPredicate(NettyHttpServerConfiguration.AccessLogger config) {
        List<String> exclusions = config.getExclusions();
        if (CollectionUtils.isEmpty(exclusions)) {
            return null;
        } else {
            // Don't do this inside the predicate to avoid compiling every request
            List<Pattern> patterns = exclusions.stream().map(Pattern::compile).collect(Collectors.toList());
            return uri -> patterns.stream().noneMatch(pattern -> pattern.matcher(uri).matches());
        }
    }

    /**
     * An HTTP server initializer for Netty.
     */
    private class NettyHttpServerInitializer extends ChannelInitializer<SocketChannel> {
        private volatile HttpPipelineBuilder httpPipelineBuilder;

        {
            initHttpCoders();
        }

        void initHttpCoders() {
            httpPipelineBuilder = new HttpPipelineBuilder(NettyHttpServer.this, nettyEmbeddedServices, sslConfiguration, routingHandler, hostResolver);
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            int port = ch.localAddress().getPort();
            boolean ssl = httpPipelineBuilder.supportsSsl() && sslConfiguration != null && port == serverPort;
            HttpPipelineBuilder.ConnectionPipeline pipelineBuilder = httpPipelineBuilder.new ConnectionPipeline(ch, ssl);

            pipelineBuilder.insertOuterTcpHandlers();

            if (serverConfiguration.getHttpVersion() != io.micronaut.http.HttpVersion.HTTP_2_0) {
                pipelineBuilder.configureForHttp1();
            } else {
                if (ssl) {
                    pipelineBuilder.configureForAlpn();
                } else {
                    pipelineBuilder.configureForH2cSupport();
                }
            }
        }
    }
}
