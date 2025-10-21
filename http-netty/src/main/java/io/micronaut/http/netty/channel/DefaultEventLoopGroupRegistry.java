/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.channel;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.netty.channel.loom.LoomCarrierGroup;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating named event loop groups.
 *
 * @author graemerocher
 * @since 2.0
 */
@Factory
@Internal
@BootstrapContextCompatible
public class DefaultEventLoopGroupRegistry implements EventLoopGroupRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventLoopGroupRegistry.class);

    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final BeanLocator beanLocator;

    private final Map<EventLoopGroup, EventLoopGroupConfiguration> eventLoopGroups = new ConcurrentHashMap<>();

    private final BeanProvider<LoomCarrierGroup.Factory> loomCarrierGroupFactory;

    /**
     * Default constructor.
     *
     * @param eventLoopGroupFactory The event loop group factory
     * @param beanLocator           The bean locator
     * @param loomCarrierGroupFactory Factory for the loom carrier group
     */
    public DefaultEventLoopGroupRegistry(EventLoopGroupFactory eventLoopGroupFactory, BeanLocator beanLocator, BeanProvider<LoomCarrierGroup.Factory> loomCarrierGroupFactory) {
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.beanLocator = beanLocator;
        this.loomCarrierGroupFactory = loomCarrierGroupFactory;
    }

    /**
     * Shut down event loop groups according to configuration.
     */
    @PreDestroy
    void shutdown() {
        eventLoopGroups.forEach((eventLoopGroup, configuration) -> {
            try {
                long quietPeriod = configuration.getShutdownQuietPeriod().toMillis();
                long timeout = configuration.getShutdownTimeout().toMillis();
                eventLoopGroup.shutdownGracefully(quietPeriod, timeout, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down EventLoopGroup: {}", t.getMessage(), t);
                }
            }
        });
        eventLoopGroups.clear();
    }

    private EventLoopGroup createGroup(EventLoopGroupConfiguration configuration, Executor executor) {
        IoHandlerFactory ioHandlerFactory = eventLoopGroupFactory.createIoHandlerFactory(configuration);
        int nThreads = numThreads(configuration);
        EventLoopGroup eventLoopGroup;
        if (configuration.isLoomCarrier()) {
            eventLoopGroup = loomCarrierGroupFactory.get().create(nThreads, executor, ioHandlerFactory);
        } else {
            eventLoopGroup = new MultiThreadIoEventLoopGroup(nThreads, executor, ioHandlerFactory);
        }
        eventLoopGroups.put(eventLoopGroup, configuration);
        return eventLoopGroup;
    }

    /**
     * Constructs an event loop group for each configuration.
     *
     * @param configuration The configuration
     * @return The event loop group
     */
    @EachBean(EventLoopGroupConfiguration.class)
    @Bean
    @BootstrapContextCompatible
    protected EventLoopGroup eventLoopGroup(EventLoopGroupConfiguration configuration) {
        String executorName = configuration.getExecutorName().orElse(null);
        Executor executor;
        if (executorName != null) {
            executor = beanLocator.findBean(Executor.class, Qualifiers.byName(executorName))
                .orElseThrow(() -> new ConfigurationException("No executor service configured for name: " + executorName));
        } else {
            ThreadFactory threadFactory = beanLocator.findBean(ThreadFactory.class, Qualifiers.byName(configuration.getName()))
                    .orElseGet(() ->  new DefaultThreadFactory(configuration.getName() + "-" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
            if (threadFactory instanceof NettyThreadFactory.EventLoopCustomizableThreadFactory custom) {
                threadFactory = custom.customizeForEventLoop();
            }
            executor = new ThreadPerTaskExecutor(threadFactory);
        }

        return createGroup(configuration, executor);
    }

    /**
     * Constructs an event loop group with default Configuration.
     *
     * @param threadFactory The default Netty thread factory
     * @return The event loop group
     */
    @Singleton
    @Requires(missingProperty = EventLoopGroupConfiguration.DEFAULT_LOOP)
    @Primary
    @BootstrapContextCompatible
    @Bean(typed = { EventLoopGroup.class })
    protected EventLoopGroup defaultEventLoopGroup(@Named(NettyThreadFactory.NAME) ThreadFactory threadFactory) {
        if (threadFactory instanceof NettyThreadFactory.EventLoopCustomizableThreadFactory custom) {
            threadFactory = custom.customizeForEventLoop();
        }
        return createGroup(new DefaultEventLoopGroupConfiguration(), new ThreadPerTaskExecutor(threadFactory));
    }

    @NonNull
    @Override
    public EventLoopGroup getDefaultEventLoopGroup() {
        return beanLocator.getBean(EventLoopGroup.class);
    }

    @Override
    public Optional<EventLoopGroup> getEventLoopGroup(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        if (EventLoopGroupConfiguration.DEFAULT.equals(name)) {
            return beanLocator.findBean(EventLoopGroup.class);
        } else {
            return beanLocator.findBean(EventLoopGroup.class, Qualifiers.byName(name));
        }
    }

    @Override
    public Optional<EventLoopGroupConfiguration> getEventLoopGroupConfiguration(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        return beanLocator.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(name));
    }

    /**
     * Calculate the number of threads from {@link EventLoopGroupConfiguration#getNumThreads()} and
     * {@link EventLoopGroupConfiguration#getThreadCoreRatio()}.
     *
     * @param configuration The configuration
     * @return The actual number of threads to use
     */
    public static int numThreads(EventLoopGroupConfiguration configuration) {
        int explicit = configuration.getNumThreads();
        if (explicit != 0) {
            return explicit;
        }
        return Math.toIntExact(Math.round(configuration.getThreadCoreRatio() * NettyRuntime.availableProcessors()));
    }
}
