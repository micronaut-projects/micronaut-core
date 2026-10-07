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
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.netty.channel.loom.LoomCarrierGroup;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
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

    private final List<TaskQueueInterceptor> taskQueueInterceptors;

    /**
     * The groups that outlive this registry, development mode's, when this registry was handed any.
     */
    private volatile @Nullable RetainedEventLoopGroups retainedGroups;

    /**
     * Default constructor.
     *
     * @param eventLoopGroupFactory The event loop group factory
     * @param beanLocator           The bean locator
     * @param loomCarrierGroupFactory Factory for the loom carrier group
     * @param taskQueueInterceptors Task queue interceptors
     */
    public DefaultEventLoopGroupRegistry(EventLoopGroupFactory eventLoopGroupFactory, BeanLocator beanLocator, BeanProvider<LoomCarrierGroup.Factory> loomCarrierGroupFactory, List<TaskQueueInterceptor> taskQueueInterceptors) {
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.beanLocator = beanLocator;
        this.loomCarrierGroupFactory = loomCarrierGroupFactory;
        this.taskQueueInterceptors = taskQueueInterceptors;
    }

    /**
     * Shut down event loop groups according to configuration.
     */
    @PreDestroy
    void shutdown() {
        RetainedEventLoopGroups retained = retainedGroups;
        Set<EventLoopGroup> leftRunning = retained == null ? Set.of() : retained.release(new ArrayList<>(eventLoopGroups.keySet()));
        eventLoopGroups.forEach((eventLoopGroup, configuration) -> {
            if (leftRunning.contains(eventLoopGroup)) {
                return;
            }
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

    private EventLoopGroup createGroup(EventLoopGroupConfiguration configuration, String name, Executor executor) {
        return createGroup(configuration, name, executor, false);
    }

    /**
     * @param frameworkThreads Whether the group runs on the framework's own threads, rather than on those of an
     * executor or thread factory bean, which may not outlive the context that created them
     */
    private EventLoopGroup createGroup(EventLoopGroupConfiguration configuration, String name, Executor executor, boolean frameworkThreads) {
        if (frameworkThreads && !configuration.isLoomCarrier() && taskQueueInterceptors.isEmpty()) {
            RetainedEventLoopGroups retained = beanLocator.findBean(RetainedEventLoopGroups.class).orElse(null);
            if (retained != null) {
                // development mode keeps the group across generations; what runs on it is each generation's own
                retainedGroups = retained;
                EventLoopGroup eventLoopGroup = retained.eventLoopGroup(name, configuration, eventLoopGroupFactory, () -> newGroup(configuration, name, executor));
                eventLoopGroups.put(eventLoopGroup, configuration);
                return eventLoopGroup;
            }
        }
        EventLoopGroup eventLoopGroup = newGroup(configuration, name, executor);
        eventLoopGroups.put(eventLoopGroup, configuration);
        return eventLoopGroup;
    }

    private EventLoopGroup newGroup(EventLoopGroupConfiguration configuration, String name, Executor executor) {
        IoHandlerFactory ioHandlerFactory = eventLoopGroupFactory.createIoHandlerFactory(configuration);
        int nThreads = numThreads(configuration);
        EventLoopGroup eventLoopGroup;
        if (configuration.isLoomCarrier()) {
            eventLoopGroup = loomCarrierGroupFactory.get().create(nThreads, executor, ioHandlerFactory);
        } else if (taskQueueInterceptors.isEmpty()) {
            eventLoopGroup = new MultiThreadIoEventLoopGroup(nThreads, executor, ioHandlerFactory);
        } else {
            eventLoopGroup = new MultiThreadIoEventLoopGroup(nThreads, executor, ioHandlerFactory) {
                @Override
                protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
                    return new SingleThreadIoEventLoop(this, executor, ioHandlerFactory) {
                        @Override
                        protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
                            Queue<Runnable> tq = super.newTaskQueue(maxPendingTasks);
                            for (TaskQueueInterceptor taskQueueInterceptor : taskQueueInterceptors) {
                                tq = taskQueueInterceptor.wrapTaskQueue(name, tq);
                            }
                            return tq;
                        }
                    };
                }
            };
        }
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
        if (executorName != null) {
            Executor executor = beanLocator.findBean(Executor.class, Qualifiers.byName(executorName))
                .orElseThrow(() -> new ConfigurationException("No executor service configured for name: " + executorName));
            return createGroup(configuration, configuration.getName(), executor);
        }
        ThreadFactory named = beanLocator.findBean(ThreadFactory.class, Qualifiers.byName(configuration.getName())).orElse(null);
        ThreadFactory threadFactory = named != null ? named
            : new DefaultThreadFactory(configuration.getName() + "-" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class));
        if (threadFactory instanceof NettyThreadFactory.EventLoopCustomizableThreadFactory custom) {
            threadFactory = custom.customizeForEventLoop();
        }
        return createGroup(configuration, configuration.getName(), new ThreadPerTaskExecutor(threadFactory), named == null);
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
        // the framework's thread factory, whose settings are the netty configuration's, rather than one of the application's
        boolean frameworkThreads = threadFactory instanceof NettyThreadFactory.EventLoopCustomizableThreadFactory;
        if (threadFactory instanceof NettyThreadFactory.EventLoopCustomizableThreadFactory custom) {
            threadFactory = custom.customizeForEventLoop();
        }
        return createGroup(new DefaultEventLoopGroupConfiguration(), EventLoopGroupConfiguration.DEFAULT, new ThreadPerTaskExecutor(threadFactory), frameworkThreads);
    }

    @Override
    public EventLoopGroup getDefaultEventLoopGroup() {
        return beanLocator.getBean(EventLoopGroup.class);
    }

    @Override
    public Optional<EventLoopGroup> getEventLoopGroup(String name) {
        ArgumentUtils.requireNonNull("name", name);
        if (EventLoopGroupConfiguration.DEFAULT.equals(name)) {
            return beanLocator.findBean(EventLoopGroup.class);
        } else {
            return beanLocator.findBean(EventLoopGroup.class, Qualifiers.byName(name));
        }
    }

    @Override
    public Optional<EventLoopGroupConfiguration> getEventLoopGroupConfiguration(String name) {
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
        double ratio = configuration.getThreadCoreRatio();
        int threads = Math.toIntExact(Math.round(ratio * NettyRuntime.availableProcessors()));
        if (ratio > 0 && threads < 1) {
            // 0 would make netty fall back to its default (2 * cores), so use at least one thread
            return 1;
        }
        return threads;
    }
}
