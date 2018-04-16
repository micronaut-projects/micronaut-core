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

import com.typesafe.netty.http.HttpStreamsServerHandler;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.server.binding.RequestBinderRegistry;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.decoders.HttpRequestDecoder;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.discovery.event.ServiceShutdownEvent;
import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.http.server.netty.ssl.NettyServerSslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.resource.StaticResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.lang.reflect.Field;
import java.net.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Implements the bootstrap and configuration logic for the Netty implementation of {@link EmbeddedServer}
 *
 * @see RoutingInBoundHandler
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class NettyHttpServer implements EmbeddedServer {
    public static final String HTTP_STREAMS_CODEC = "http-streams-codec";
    public static final String HTTP_CODEC = "http-codec";
    public static final String HTTP_COMPRESSOR = "http-compressor";
    public static final String MICRONAUT_HANDLER = "micronaut-inbound-handler";
    public static final String OUTBOUND_KEY = "-outbound-";
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final ExecutorService ioExecutor;
    private final ExecutorSelector executorSelector;
    private final ChannelOutboundHandler[] outboundHandlers;
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    private final NettyHttpServerConfiguration serverConfiguration;
    private final SslConfiguration sslConfiguration;
    private final StaticResourceResolver staticResourceResolver;
    private final Environment environment;
    private final Router router;
    private final RequestBinderRegistry binderRegistry;
    private final BeanLocator beanLocator;
    private final ThreadFactory threadFactory;
    private volatile int serverPort;
    private final ApplicationContext applicationContext;
    private final Optional<SslContext> sslContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private NioEventLoopGroup workerGroup;
    private NioEventLoopGroup parentGroup;
    private EmbeddedServerInstance serviceInstance;


    @Inject
    public NettyHttpServer(
            NettyHttpServerConfiguration serverConfiguration,
            ApplicationContext applicationContext,
            Router router,
            RequestBinderRegistry binderRegistry,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
            StaticResourceResolver resourceResolver,
            @javax.inject.Named(TaskExecutors.IO) ExecutorService ioExecutor,
            @javax.inject.Named(NettyThreadFactory.NAME) ThreadFactory threadFactory,
            ExecutorSelector executorSelector,
            NettyServerSslBuilder nettyServerSslBuilder,
            ChannelOutboundHandler... outboundHandlers
    ) {
        Optional<File> location = serverConfiguration.getMultipart().getLocation();
        location.ifPresent(dir ->
                DiskFileUpload.baseDirectory = dir.getAbsolutePath()
        );
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
        this.binderRegistry = binderRegistry;
        this.staticResourceResolver = resourceResolver;
        this.sslContext = nettyServerSslBuilder.build();
        this.threadFactory = threadFactory;
    }

    /**
     * @return The configuration for the server
     */
    public NettyHttpServerConfiguration getServerConfiguration() {
        return serverConfiguration;
    }

    @Override
    public boolean isRunning() {
        return running.get() && !SocketUtils.isTcpPortAvailable(serverPort);
    }

    @Override
    public synchronized EmbeddedServer start() {
        if(!isRunning()) {

            workerGroup = createWorkerEventLoopGroup();
            parentGroup = createParentEventLoopGroup();
            ServerBootstrap serverBootstrap = createServerBootstrap();

            processOptions(serverConfiguration.getOptions(), serverBootstrap::option);
            processOptions(serverConfiguration.getChildOptions(), serverBootstrap::childOption);


            serverBootstrap = serverBootstrap.group(parentGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            RequestBinderRegistry binderRegistry = NettyHttpServer.this.binderRegistry;

                            sslContext.ifPresent(ctx ->
                                pipeline.addLast(ctx.newHandler(ch.alloc()))
                            );

                            pipeline.addLast(new IdleStateHandler(
                                    (int)serverConfiguration.getReadIdleTime().getSeconds(),
                                    (int)serverConfiguration.getWriteIdleTime().getSeconds(),
                                    (int)serverConfiguration.getIdleTime().getSeconds()));
                            pipeline.addLast(HTTP_CODEC, new HttpServerCodec());
                            pipeline.addLast(HTTP_COMPRESSOR, new SmartHttpContentCompressor());
                            pipeline.addLast(HTTP_STREAMS_CODEC, new HttpStreamsServerHandler());
                            pipeline.addLast(HttpRequestDecoder.ID, new HttpRequestDecoder(NettyHttpServer.this, environment, serverConfiguration));
                            pipeline.addLast(MICRONAUT_HANDLER, new RoutingInBoundHandler(
                                    beanLocator,
                                    router,
                                    mediaTypeCodecRegistry,
                                    customizableResponseTypeHandlerRegistry,
                                    staticResourceResolver,
                                    serverConfiguration,
                                    binderRegistry,
                                    executorSelector,
                                    ioExecutor
                            ));
                            registerMicronautChannelHandlers(pipeline);
                        }
                    });

            Optional<String> host = serverConfiguration.getHost();

            bindServerToHost(serverBootstrap, host, new AtomicInteger(0));
            running.set(true);
        }

        return this;
    }

    private void bindServerToHost(ServerBootstrap serverBootstrap, Optional<String> host, AtomicInteger attempts) {
        boolean isRandomPort = serverConfiguration.getPort() == -1;
        if(!SocketUtils.isTcpPortAvailable(serverPort) && !isRandomPort) {
            throw new ServerStartupException("Unable to start Micronaut server on port: " + serverPort, new BindException("Address already in use"));
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("Binding server to port: {}", serverPort);
        }
        try {
            if(host.isPresent()) {
                serverBootstrap.bind(host.get(), serverPort).sync();
            }
            else {
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
                if(e instanceof BindException) {
                    LOG.error("Unable to start server. Port already {} in use.", serverPort);
                }
                else {
                    LOG.error("Error starting Micronaut server: " + e.getMessage(), e);
                }
            }
            int attemptCount = attempts.getAndIncrement();

            if(isRandomPort && attemptCount < 3) {
                serverPort = SocketUtils.findAvailableTcpPort();
                bindServerToHost(serverBootstrap, host, attempts);
            }
            else {
                stop();
            }
        }

    }

    @Override
    public synchronized EmbeddedServer stop() {
        if (isRunning() && workerGroup != null) {
            if(running.compareAndSet(true,false)) {

                try {
                    workerGroup.shutdownGracefully()
                            .addListener(this::logShutdownErrorIfNecessary);
                    parentGroup.shutdownGracefully()
                            .addListener(this::logShutdownErrorIfNecessary);
                    applicationContext.publishEvent(new ServerShutdownEvent(this));
                    if(serviceInstance != null) {
                        applicationContext.publishEvent(new ServiceShutdownEvent(serviceInstance));
                    }
                    if(applicationContext.isRunning()) {
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

    private void logShutdownErrorIfNecessary(Future<?> future) {
        if(!future.isSuccess()) {
            if (LOG.isWarnEnabled()) {
                Throwable e = future.cause();
                LOG.warn("Error stopping Micronaut server: " + e.getMessage(), e);
            }
        }
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

    protected NioEventLoopGroup createParentEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getParent());
    }

    protected NioEventLoopGroup createWorkerEventLoopGroup() {
        return newEventLoopGroup(serverConfiguration.getWorker());
    }

    protected ServerBootstrap createServerBootstrap() {
        return new ServerBootstrap();
    }


    private NioEventLoopGroup newEventLoopGroup(NettyHttpServerConfiguration.EventLoopConfig config) {
        if (config != null) {
            Optional<ExecutorService> executorService = config.getExecutorName().flatMap(name -> beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name)));
            NioEventLoopGroup group = executorService.map(service ->
                    new NioEventLoopGroup(config.getNumOfThreads(), service)
            ).orElseGet(() ->
                    {
                        if(threadFactory != null) {
                            return new NioEventLoopGroup(config.getNumOfThreads(), threadFactory);
                        }
                        else {
                            return new NioEventLoopGroup(config.getNumOfThreads());
                        }
                    }
            );
            config.getIoRatio().ifPresent(group::setIoRatio);
            return group;
        } else {
            if(threadFactory != null) {
                return new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            }
            else {
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
}
