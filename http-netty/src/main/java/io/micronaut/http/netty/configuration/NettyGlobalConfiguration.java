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
package io.micronaut.http.netty.configuration;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.netty.util.ResourceLeakDetector;

/**
 * Allows configuring Netty global properties.
 *
 * @author Denis Stepannov
 * @since 2.5.0
 */
@ConfigurationProperties("netty")
@BootstrapContextCompatible
public class NettyGlobalConfiguration {

    private static final boolean DEFAULT_THREAD_FACTORY_REACTOR_NON_BLOCKING = true;
    private static final boolean DEFAULT_THREAD_FACTORY_DAEMON = false;
    private static final int DEFAULT_THREAD_FACTORY_PRIORITY = Thread.NORM_PRIORITY;

    private ResourceLeakDetector.Level resourceLeakDetectorLevel;
    private boolean defaultThreadFactoryReactorNonBlocking = DEFAULT_THREAD_FACTORY_REACTOR_NON_BLOCKING;
    private boolean defaultThreadFactoryDaemon = DEFAULT_THREAD_FACTORY_DAEMON;
    private int defaultThreadFactoryPriority = DEFAULT_THREAD_FACTORY_PRIORITY;

    /**
     * Sets the resource leak detection level.
     *
     * @param resourceLeakDetectorLevel the resource leak detection level
     */
    public void setResourceLeakDetectorLevel(ResourceLeakDetector.Level resourceLeakDetectorLevel) {
        this.resourceLeakDetectorLevel = resourceLeakDetectorLevel;
    }

    /**
     * Provides the value set for the resource leak detection.
     *
     * @return the resource leak detection level
     */
    @Nullable
    public ResourceLeakDetector.Level getResourceLeakDetectorLevel() {
        return resourceLeakDetectorLevel;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_REACTOR_NON_BLOCKING}.
     * If {@code true}, netty event loop threads will implement project reactor {@link reactor.core.scheduler.NonBlocking} by default.
     * Because of that, any Project Reactor's blocking operations throw an exception on those threads.
     *
     * @return Whether event loop threads should implement NonBlocking by default
     */
    public boolean isDefaultThreadFactoryReactorNonBlocking() {
        return defaultThreadFactoryReactorNonBlocking;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_REACTOR_NON_BLOCKING}.
     * If {@code true}, netty event loop threads will implement project reactor {@link reactor.core.scheduler.NonBlocking} by default.
     * Because of that, any Project Reactor's blocking operations throw an exception on those threads.
     *
     * @param defaultThreadFactoryReactorNonBlocking Whether event loop threads should implement
     *                                               NonBlocking by default
     */
    public void setDefaultThreadFactoryReactorNonBlocking(boolean defaultThreadFactoryReactorNonBlocking) {
        this.defaultThreadFactoryReactorNonBlocking = defaultThreadFactoryReactorNonBlocking;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_DAEMON}.
     *
     * @return defaultThreadFactoryDaemon are created threads will be a daemon or not
     */
    public boolean isDefaultThreadFactoryDaemon() {
        return defaultThreadFactoryDaemon;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_DAEMON}.
     *
     * @param defaultThreadFactoryDaemon are created threads will be a daemon or not
     */
    public void setDefaultThreadFactoryDaemon(boolean defaultThreadFactoryDaemon) {
        this.defaultThreadFactoryDaemon = defaultThreadFactoryDaemon;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_DAEMON}.
     *
     * @return defaultThreadFactoryPriority created threads priority
     */
    public int getDefaultThreadFactoryPriority() {
        return defaultThreadFactoryPriority;
    }

    /**
     * Default value: {@value #DEFAULT_THREAD_FACTORY_DAEMON}.
     * Minimum value: {@value Thread.MIN_PRIORITY}, maximum value: {@value Thread.MAX_PRIORITY}
     *
     * @param defaultThreadFactoryPriority created threads priority
     */
    public void setDefaultThreadFactoryPriority(int defaultThreadFactoryPriority) {
        if (defaultThreadFactoryPriority < Thread.MIN_PRIORITY || defaultThreadFactoryPriority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("defaultThreadFactoryPriority: " + defaultThreadFactoryPriority + " (expected: Thread.MIN_PRIORITY <= defaultThreadFactoryPriority <= Thread.MAX_PRIORITY)");
        }

        this.defaultThreadFactoryPriority = defaultThreadFactoryPriority;
    }
}
