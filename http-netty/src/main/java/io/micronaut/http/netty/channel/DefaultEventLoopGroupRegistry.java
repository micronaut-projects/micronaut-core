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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.*;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

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
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     *
     * @param eventLoopGroupFactory The event loop group factory
     * @param beanLocator           The bean locator
     */
    public DefaultEventLoopGroupRegistry(EventLoopGroupFactory eventLoopGroupFactory, BeanLocator beanLocator) {
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.beanLocator = beanLocator;
    }

    /**
     * Constructs an event loop group for each configuration.
     *
     * @param configuration The configuration
     * @return The event loop group
     */
    @EachBean(EventLoopGroupConfiguration.class)
    @Bean(preDestroy = "shutdownGracefully")
    @BootstrapContextCompatible
    protected EventLoopGroup eventLoopGroup(EventLoopGroupConfiguration configuration) {
        final String executor = configuration.getExecutorName().orElse(null);
        if (executor != null) {
            return beanLocator.findBean(Executor.class, Qualifiers.byName(executor))
                              .map(executorService -> eventLoopGroupFactory.createEventLoopGroup(
                                      configuration.getNumThreads(),
                                      executorService,
                                      configuration.getIoRatio().orElse(null)
                              )).orElseThrow(() -> new ConfigurationException("No executor service configured for name: " + executor));
        } else {
            ThreadFactory threadFactory = beanLocator.findBean(ThreadFactory.class, Qualifiers.byName(configuration.getName()))
                    .orElseGet(() ->  new DefaultThreadFactory(configuration.getName() + "-" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
            return eventLoopGroupFactory.createEventLoopGroup(configuration, threadFactory);
        }
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
    @Bean(preDestroy = "shutdownGracefully")
    @BootstrapContextCompatible
    protected EventLoopGroup defaultEventLoopGroup(@Named(NettyThreadFactory.NAME) ThreadFactory threadFactory) {
        return eventLoopGroupFactory.createEventLoopGroup(new DefaultEventLoopGroupConfiguration(), threadFactory);
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
}
