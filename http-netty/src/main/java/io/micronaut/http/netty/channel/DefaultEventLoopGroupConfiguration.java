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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration interface for event loop configuration.
 *
 * @author graemerocher
 * @since 2.0
 */
@EachProperty(value = EventLoopGroupConfiguration.EVENT_LOOPS, primary = "default")
public class DefaultEventLoopGroupConfiguration implements EventLoopGroupConfiguration {

    private final int numThreads;
    private final Integer ioRatio;
    private final boolean preferNativeTransport;
    private final String name;
    private final String executor;
    private final Duration shutdownQuietPeriod;
    private final Duration shutdownTimeout;

    /**
     * Default constructor.
     *
     * @param name                  The name of the group
     * @param numThreads            The number of threads
     * @param ioRatio               The IO ratio (optional)
     * @param preferNativeTransport Whether native transport is to be preferred
     * @param executor              A named executor service to use (optional)
     * @param shutdownQuietPeriod   The shutdown quiet period
     * @param shutdownTimeout       The shutdown timeout (must be >= shutdownQuietPeriod)
     */
    @ConfigurationInject
    public DefaultEventLoopGroupConfiguration(
            @Parameter String name,
            @Bindable(defaultValue = "0") int numThreads,
            @Nullable Integer ioRatio,
            @Bindable(defaultValue = StringUtils.FALSE) boolean preferNativeTransport,
            @Nullable String executor,
            @Nullable Duration shutdownQuietPeriod,
            @Nullable Duration shutdownTimeout
    ) {
        this.name = name;
        this.numThreads = numThreads;
        this.ioRatio = ioRatio;
        this.preferNativeTransport = preferNativeTransport;
        this.executor = executor;
        this.shutdownQuietPeriod = Optional.ofNullable(shutdownQuietPeriod)
            .orElse(Duration.ofSeconds(DEFAULT_SHUTDOWN_QUIET_PERIOD));
        this.shutdownTimeout = Optional.ofNullable(shutdownTimeout)
            .orElse(Duration.ofSeconds(DEFAULT_SHUTDOWN_TIMEOUT));
    }

    /**
     * Default constructor.
     */
    public DefaultEventLoopGroupConfiguration() {
        this.name = DEFAULT;
        this.numThreads = 0;
        this.ioRatio = null;
        this.preferNativeTransport = false;
        this.executor = null;
        this.shutdownQuietPeriod = Duration.ofSeconds(DEFAULT_SHUTDOWN_QUIET_PERIOD);
        this.shutdownTimeout = Duration.ofSeconds(DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * @return The number of threads for the event loop
     */
    @Override
    public int getNumThreads() {
        return numThreads;
    }

    /**
     * @return The I/O ratio.
     */
    @Override
    public Optional<Integer> getIoRatio() {
        return Optional.ofNullable(ioRatio);
    }

    @Override
    public Optional<String> getExecutorName() {
        return Optional.ofNullable(executor);
    }

    /**
     * @return Whether to prefer the native transport
     */
    @Override
    public boolean isPreferNativeTransport() {
        return preferNativeTransport;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Duration getShutdownQuietPeriod() {
        return shutdownQuietPeriod;
    }

    @Override
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }
}
