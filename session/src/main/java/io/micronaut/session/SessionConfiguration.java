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

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;

/**
 * <p>Base configuration properties for session handling.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(SessionSettings.PREFIX)
public class SessionConfiguration {

    /**
     * The default max inactive interval in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXINACTIVEINTERVAL_SECONDS = 30;

    private Duration maxInactiveInterval = Duration.ofMinutes(DEFAULT_MAXINACTIVEINTERVAL_SECONDS);
    private Integer maxActiveSessions;
    private boolean promptExpiration = false;
    private ExecutorService executorService;

    public void SessionConfiguration() { }

    /**
     * Injected Executor Service to enable scheduled expiration of session
     */
    @Inject
    public void SessionConfiguration(@Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * @return The maximum number of active sessions
     */
    public OptionalInt getMaxActiveSessions() {
        return maxActiveSessions != null ? OptionalInt.of(maxActiveSessions) : OptionalInt.empty();
    }

    /**
     * Sets the maximum number of active sessions. Default value ({@value #DEFAULT_MAXINACTIVEINTERVAL_SECONDS} seconds).
     *
     * @param maxActiveSessions The max active sessions
     */
    public void setMaxActiveSessions(Integer maxActiveSessions) {
        this.maxActiveSessions = maxActiveSessions;
    }

    /**
     * @return The default max inactive interval
     */
    public Duration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    /**
     * Set the max active sessions.
     *
     * @param maxInactiveInterval The max inactive interval
     */
    public void setMaxInactiveInterval(Duration maxInactiveInterval) {
        if (maxInactiveInterval != null) {
            this.maxInactiveInterval = maxInactiveInterval;
        }
    }

    /**
     * @return if prompt expiration is enabled
     */
    public boolean isPromptExpiration() {
        return promptExpiration;
    }

    /**
     * Set if prompt expiration is enabled
     *
     * @param promptExpiration if prompt expiration is enabled / disabled
     */
    public void setPromptExpiration(boolean promptExpiration) {
        this.promptExpiration = promptExpiration;
    }

    /**
     * @return The injected executor service
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Set the executor service
     *
     * @param executorService The executorService
     */
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
}
