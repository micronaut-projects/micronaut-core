/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.session;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.scheduling.TaskExecutors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * <p>Base configuration properties for session handling.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(SessionSettings.PREFIX)
public class SessionConfiguration {

    /**
     * @deprecated Use {@link #DEFAULT_MAXINACTIVEINTERVAL_MINUTES} instead.
     */
    @Deprecated
    public static final int DEFAULT_MAXINACTIVEINTERVAL_SECONDS = 30;

    /**
     * The default max inactive interval in minutes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXINACTIVEINTERVAL_MINUTES = 30;

    private Duration maxInactiveInterval = Duration.ofMinutes(DEFAULT_MAXINACTIVEINTERVAL_MINUTES);
    private Integer maxActiveSessions;
    private boolean promptExpiration = false;
    private Provider<ExecutorService> executorService;

    /**
     * @return The maximum number of active sessions
     */
    public OptionalInt getMaxActiveSessions() {
        return maxActiveSessions != null ? OptionalInt.of(maxActiveSessions) : OptionalInt.empty();
    }

    /**
     * Sets the maximum number of active sessions.
     *
     * @param maxActiveSessions The max active sessions
     */
    public void setMaxActiveSessions(Integer maxActiveSessions) {
        this.maxActiveSessions = maxActiveSessions;
    }

    /**
     * @return The maximum inactive interval
     */
    public Duration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    /**
     * Set the maximum inactive interval. Default value ({@value #DEFAULT_MAXINACTIVEINTERVAL_MINUTES} minutes).
     *
     * @param maxInactiveInterval The max inactive interval
     */
    public void setMaxInactiveInterval(Duration maxInactiveInterval) {
        if (maxInactiveInterval != null) {
            this.maxInactiveInterval = maxInactiveInterval;
        }
    }

    /**
     * @return if prompt expiration is enabled.
     */
    public boolean isPromptExpiration() {
        return promptExpiration;
    }

    /**
     * Set if prompt expiration is enabled.
     *
     * @param promptExpiration if prompt expiration is enabled / disabled
     */
    public void setPromptExpiration(boolean promptExpiration) {
        this.promptExpiration = promptExpiration;
    }

    /**
     * @return The injected executor service
     */
    public Optional<ScheduledExecutorService> getExecutorService() {
        return Optional.ofNullable(executorService)
                .map(Provider::get)
                .filter(ScheduledExecutorService.class::isInstance)
                .map(ScheduledExecutorService.class::cast);
    }

    /**
     * Set the executor service.
     *
     * @param executorService The executorService
     */
    @Inject
    public void setExecutorService(@Nullable @Named(TaskExecutors.SCHEDULED) Provider<ExecutorService> executorService) {
        this.executorService = executorService;
    }
}
