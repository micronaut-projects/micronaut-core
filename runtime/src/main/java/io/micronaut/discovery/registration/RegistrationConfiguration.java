/*
 * Copyright 2018 original authors
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
import io.micronaut.core.util.Toggleable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Common configuration for {@link io.micronaut.discovery.ServiceInstance} registration
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class RegistrationConfiguration implements Toggleable {

    public static final String PREFIX = "registration";

    private String healthPath;

    private int retryCount = -1;

    private Duration timeout;

    private Duration retryDelay = Duration.of(1, ChronoUnit.SECONDS);

    private boolean failFast = true;

    private boolean enabled = true;

    private boolean deregister = true;

    /**
     * @return The default timeout for registration
     */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    /**
     * @return Whether to fail server startup if registration fails
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * @return Whether to deregister the service on shutdown
     */
    public boolean isDeregister() {
        return deregister;
    }

    /**
     * @return Whether service registration is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The number of times to retry registration
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * @return The default retry delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * @return The path to the health endpoint
     */
    public Optional<String> getHealthPath() {
        return Optional.ofNullable(healthPath);
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDeregister(boolean deregister) {
        this.deregister = deregister;
    }
}
