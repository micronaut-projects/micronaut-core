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
package io.micronaut.discovery.registration;

import io.micronaut.core.util.Toggleable;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Common configuration for {@link io.micronaut.discovery.ServiceInstance} registration.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class RegistrationConfiguration implements Toggleable {

    /**
     * The prefix to use for all client discovery registration settings.
     */
    public static final String PREFIX = "registration";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default retry count value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_RETRY_COUNT = -1;

    /**
     * The default retry delay in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_RETRYDELAY_SECONDS = 1;

    /**
     * The default deregister value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_DEREGISTER = true;

    /**
     * The default fail fast value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FAILFAST = true;

    private String healthPath;
    private int retryCount = DEFAULT_RETRY_COUNT;
    private Duration timeout;
    private Duration retryDelay = Duration.of(DEFAULT_RETRYDELAY_SECONDS, ChronoUnit.SECONDS);
    private boolean failFast = DEFAULT_FAILFAST;
    private boolean enabled = DEFAULT_ENABLED;
    private boolean deregister = DEFAULT_DEREGISTER;
    private boolean preferIpAddress = false;
    private String ipAddr;

    /**
     * The IP address to use to register.
     *
     * @return The IP address.
     */
    public Optional<String> getIpAddr() {
        return Optional.ofNullable(ipAddr);
    }

    /**
     * The IP address to use to register.
     *
     * @param ipAddr The ip address
     */
    public void setIpAddr(@Nullable String ipAddr) {
        this.ipAddr = ipAddr;
    }

    /**
     * Should the IP address by used to register with the discovery server. Defaults to false.
     * @return True if the IP address should be used.
     */
    public boolean isPreferIpAddress() {
        return preferIpAddress;
    }

    /**
     * Sets whether the IP address by used to register with the discovery server. Defaults to false.
     * @param preferIpAddress True if the IP address should be used
     */
    public void setPreferIpAddress(boolean preferIpAddress) {
        this.preferIpAddress = preferIpAddress;
    }

    /**
     * @return The default timeout for registration
     */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    /**
     * @param timeout The timeout for registration
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * @return Whether to fail server startup if registration fails
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Default value ({@value #DEFAULT_FAILFAST}).
     * @param failFast Whether to fail server startup if registration fails
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * @return Whether to deregister the service on shutdown
     */
    public boolean isDeregister() {
        return deregister;
    }

    /**
     * Default value ({@value #DEFAULT_DEREGISTER}).
     * @param deregister Whether to deregister the service on shutdown
     */
    public void setDeregister(boolean deregister) {
        this.deregister = deregister;
    }

    /**
     * @return Whether service registration is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Whether service registration is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The number of times to retry registration
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Default value ({@value #DEFAULT_RETRY_COUNT}).
     * @param retryCount The retry count
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * @return The default retry delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * Default value ({@value #DEFAULT_RETRYDELAY_SECONDS} seconds).
     * @param retryDelay The retry delay
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * @return The path to the health endpoint
     */
    public Optional<String> getHealthPath() {
        return Optional.ofNullable(healthPath);
    }

    /**
     * @param healthPath The health endpoint path
     */
    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }
}
