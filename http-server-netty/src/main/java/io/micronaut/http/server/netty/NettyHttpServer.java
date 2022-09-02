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
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    private final RoutingInBoundHandler routingHandler;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final boolean isDefault;
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
    @Nullable
    private volatile List<Listener> activeListeners = null;
    private final List<NettyHttpServerConfiguration.NettyListenerConfiguration> listenerConfigurations;
    private final CompositeNettyServerCustomizer rootCustomizer = new CompositeNettyServerCustomizer();

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

        final ServerSslBuilder serverSslBuilder = nettyEmbeddedServices.getServerSslBuilder();
        if (serverSslBuilder != null) {
            this.sslConfiguration = serverSslBuilder.getSslConfiguration();
        } else {
            this.sslConfiguration = null;
        }
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

        this.listenerConfigurations = buildListenerConfigurations();
    }

    private List<NettyHttpServerConfiguration.NettyListenerConfiguration> buildListenerConfigurations() {
        List<NettyHttpServerConfiguration.NettyListenerConfiguration> explicit = serverConfiguration.getListeners();
        if (explicit != null) {
            if (explicit.isEmpty()) {
                throw new IllegalArgumentException("When configuring listeners explicitly, must specify at least one");
            }
            return explicit;
        } else {
            String configuredHost = serverConfiguration.getHost().orElse(null);
            List<NettyHttpServerConfiguration.NettyListenerConfiguration> implicit = new ArrayList<>(2);
            final ServerSslBuilder serverSslBuilder = nettyEmbeddedServices.getServerSslBuilder();
            if (serverSslBuilder != null && this.sslConfiguration.isEnabled()) {
                implicit.add(NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(configuredHost, sslConfiguration.getPort(), true));
            } else {
                implicit.add(NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(configuredHost, getHttpPort(serverConfiguration), false));
            }
            if (isDefault) {
                if (serverConfiguration.isDualProtocol()) {
                    implicit.add(NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(configuredHost, getHttpPort(serverConfiguration), false));
                }
                final Router router = this.nettyEmbeddedServices.getRouter();
                final Set<Integer> exposedPorts = router.getExposedPorts();
                for (int exposedPort : exposedPorts) {
                    if (exposedPort == -1 || exposedPort == 0 || implicit.stream().noneMatch(cfg -> cfg.getPort() == exposedPort)) {
                        NettyHttpServerConfiguration.NettyListenerConfiguration mgmt = NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(configuredHost, exposedPort, false);
                        mgmt.setExposeDefaultRoutes(false);
                        implicit.add(mgmt);
                    }
                }
            }
            return implicit;
        }
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
    @NonNull
    public synchronized NettyEmbeddedServer start() {
        if (!isRunning()) {
            if (isDefault && !applicationContext.isRunning()) {
                applicationContext.start();
            }
            //suppress unused
            //done here to prevent a blocking service loader in the event loop
            EventLoopGroupConfiguration workerConfig = resolveWorkerConfiguration();
            workerGroup = createWorkerEventLoopGroup(workerConfig);
            parentGroup = createParentEventLoopGroup();
            ServerBootstrap serverBootstrap = createServerBootstrap();

            processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
            processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);
            serverBootstrap = serverBootstrap.group(parentGroup, workerGroup);

            List<Listener> listeners = new ArrayList<>();
            for (NettyHttpServerConfiguration.NettyListenerConfiguration listenerConfiguration : listenerConfigurations) {
                Listener listener = bind(serverBootstrap, listenerConfiguration, workerConfig);
                listeners.add(listener);
            }
            this.activeListeners = Collections.unmodifiableList(listeners);

            if (isDefault) {
                final Router router = this.nettyEmbeddedServices.getRouter();
                final Set<Integer> exposedPorts = router.getExposedPorts();
                if (CollectionUtils.isNotEmpty(exposedPorts)) {
                    router.applyDefaultPorts(listeners.stream()
                            .filter(l -> l.config.isExposeDefaultRoutes())
                            .map(l -> l.serverChannel.localAddress())
                            .filter(InetSocketAddress.class::isInstance)
                            .map(addr -> ((InetSocketAddress) addr).getPort())
                            .collect(Collectors.toList()));
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
    @NonNull
    public synchronized NettyEmbeddedServer stop() {
        return stop(true);
    }

    @Override
    @NonNull
    public NettyEmbeddedServer stopServerOnly() {
        return stop(false);
    }

    @NonNull
    private NettyEmbeddedServer stop(boolean stopApplicationContext) {
        if (isRunning() && workerGroup != null) {
            if (running.compareAndSet(true, false)) {
                stopInternal(stopApplicationContext);
            }
        }
        return this;
    }

    @Override
    public void register(@NonNull NettyServerCustomizer customizer) {
        Objects.requireNonNull(customizer, "customizer");
        rootCustomizer.add(customizer);
    }

    @Override
    public int getPort() {
        List<Listener> listenersLocal = this.activeListeners;

        // flags for determining failure reason
        boolean hasRandom = false;
        boolean hasUnix = false;
        if (listenersLocal == null) {
            // not started, try to infer from config
            for (NettyHttpServerConfiguration.NettyListenerConfiguration listenerCfg : listenerConfigurations) {
                switch (listenerCfg.getFamily()) {
                    case TCP:
                        if (listenerCfg.getPort() == -1) {
                            hasRandom = true;
                        } else {
                            // found one \o/
                            return listenerCfg.getPort();
                        }
                        break;
                    case UNIX:
                        hasUnix = true;
                        break;
                    default:
                        // unknown
                }
            }
        } else {
            // started already, just use the localAddress() of each channel
            for (Listener listener : listenersLocal) {
                SocketAddress localAddress = listener.serverChannel.localAddress();
                if (localAddress instanceof InetSocketAddress) {
                    // found one \o/
                    return ((InetSocketAddress) localAddress).getPort();
                } else {
                    hasUnix = true;
                }
            }
        }
        // no eligible port
        if (hasRandom) {
            throw new UnsupportedOperationException("Retrieving the port from the server before it has started is not supported when binding to a random port");
        } else if (hasUnix) {
            throw new UnsupportedOperationException("Retrieving the port from the server is not supported for unix domain sockets");
        } else {
            throw new UnsupportedOperationException("Could not retrieve server port");
        }
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
        List<Listener> listeners = activeListeners;
        if (listeners == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(listeners.stream()
                .map(l -> l.serverChannel.localAddress())
                .filter(InetSocketAddress.class::isInstance)
                .map(addr -> ((InetSocketAddress) addr).getPort())
                .collect(Collectors.<Integer, Set<Integer>>toCollection(LinkedHashSet::new)));
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

    private Listener bind(ServerBootstrap bootstrap, NettyHttpServerConfiguration.NettyListenerConfiguration cfg, EventLoopGroupConfiguration workerConfig) {
        logBind(cfg);

        try {
            Listener listener = new Listener(cfg);
            ServerBootstrap listenerBootstrap = bootstrap.clone()
                // this initializer runs before the actual bind operation, so we can be sure
                // setServerChannel has been called by the time bind runs.
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) {
                        listener.setServerChannel(ch);
                    }
                })
                .childHandler(listener);
            ChannelFuture future;
            switch (cfg.getFamily()) {
                case TCP:
                    listenerBootstrap.channelFactory(() -> nettyEmbeddedServices.getServerSocketChannelInstance(workerConfig));
                    int port = cfg.getPort();
                    if (port == -1) {
                        port = 0;
                    }
                    if (cfg.getHost() == null) {
                        future = listenerBootstrap.bind(port);
                    } else {
                        future = listenerBootstrap.bind(cfg.getHost(), port);
                    }
                    break;
                case UNIX:
                    listenerBootstrap.channelFactory(() -> nettyEmbeddedServices.getDomainServerChannelInstance(workerConfig));
                    future = listenerBootstrap.bind(DomainSocketHolder.makeDomainSocketAddress(cfg.getPath()));
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported family: " + cfg.getFamily());
            }
            future.syncUninterruptibly();
            return listener;
        } catch (Exception e) {
            // syncUninterruptibly will rethrow a checked BindException as unchecked, so this value can be true
            @SuppressWarnings("ConstantConditions")
            final boolean isBindError = e instanceof BindException;
            if (LOG.isErrorEnabled()) {
                //noinspection ConstantConditions
                if (isBindError) {
                    LOG.error("Unable to start server. Port {} already in use.", displayAddress(cfg));
                } else {
                    LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                }
            }
            stopInternal(true);
            throw new ServerStartupException("Unable to start Micronaut server on " + displayAddress(cfg), e);
        }
    }

    private void logBind(NettyHttpServerConfiguration.NettyListenerConfiguration cfg) {
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        if (applicationName.isPresent()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Binding {} server to {}", applicationName.get(), displayAddress(cfg));
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Binding server to {}", displayAddress(cfg));
            }
        }
    }

    private static String displayAddress(NettyHttpServerConfiguration.NettyListenerConfiguration cfg) {
        switch (cfg.getFamily()) {
            case TCP:
                if (cfg.getHost() == null) {
                    return "*:" + cfg.getPort();
                } else {
                    return cfg.getHost() + ":" + cfg.getPort();
                }
            case UNIX:
                if (cfg.getPath().startsWith("\0")) {
                    return "unix:@" + cfg.getPath().substring(1);
                } else {
                    return "unix:" + cfg.getPath();
                }
            default:
                throw new UnsupportedOperationException("Unsupported family: " + cfg.getFamily());
        }
    }

    private void fireStartupEvents() {
        Optional<String> applicationName = serverConfiguration.getApplicationConfiguration().getName();
        applicationContext.getEventPublisher(ServerStartupEvent.class)
                .publishEvent(new ServerStartupEvent(this));
        applicationName.ifPresent(id -> {
            if (serviceInstance == null) {
                serviceInstance = applicationContext.createBean(NettyEmbeddedServerInstance.class, id, this);
            }
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

    private void stopInternal(boolean stopApplicationContext) {
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
            if (isDefault && applicationContext.isRunning() && stopApplicationContext) {
                applicationContext.stop();
            }
            serverConfiguration.getMultipart().getLocation().ifPresent(dir -> DiskFileUpload.baseDirectory = null);
            this.activeListeners = null;
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
        List<Listener> listeners = activeListeners;
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.refresh();
            }
        }
    }

    final void triggerPipelineListeners(ChannelPipeline pipeline) {
        for (ChannelPipelineListener pipelineListener : pipelineListeners) {
            pipelineListener.onConnect(pipeline);
        }
    }

    private HttpPipelineBuilder createPipelineBuilder(NettyServerCustomizer customizer) {
        Objects.requireNonNull(customizer, "customizer");
        return new HttpPipelineBuilder(NettyHttpServer.this, nettyEmbeddedServices, sslConfiguration, routingHandler, hostResolver, customizer);
    }

    /**
     * Builds Embedded Channel.
     *
     * @param ssl SSL
     * @return Embedded Channel
     */
    @Internal
    public EmbeddedChannel buildEmbeddedChannel(boolean ssl) {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        createPipelineBuilder(rootCustomizer).new ConnectionPipeline(embeddedChannel, ssl).initChannel();
        return embeddedChannel;
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

    private class Listener extends ChannelInitializer<Channel> {
        Channel serverChannel;
        private NettyServerCustomizer listenerCustomizer;
        NettyHttpServerConfiguration.NettyListenerConfiguration config;

        private volatile HttpPipelineBuilder httpPipelineBuilder;

        Listener(NettyHttpServerConfiguration.NettyListenerConfiguration config) {
            this.config = config;
        }

        void refresh() {
            httpPipelineBuilder = createPipelineBuilder(listenerCustomizer);
            if (config.isSsl() && !httpPipelineBuilder.supportsSsl()) {
                throw new IllegalStateException("Listener configured for SSL, but no SSL context available");
            }
        }

        void setServerChannel(Channel serverChannel) {
            this.serverChannel = serverChannel;
            this.listenerCustomizer = rootCustomizer.specializeForChannel(serverChannel, NettyServerCustomizer.ChannelRole.LISTENER);
            refresh();
        }

        @Override
        protected void initChannel(@NonNull Channel ch) throws Exception {
            httpPipelineBuilder.new ConnectionPipeline(ch, config.isSsl()).initChannel();
        }
    }

    private static class DomainSocketHolder {
        @NonNull
        private static SocketAddress makeDomainSocketAddress(String path) {
            try {
                return new DomainSocketAddress(path);
            } catch (NoClassDefFoundError e) {
                throw new UnsupportedOperationException("Netty domain socket support not on classpath", e);
            }
        }
    }
}
