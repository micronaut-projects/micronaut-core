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
     * Injected Executor Service to enable scheduled expiration of session
     */
    @Inject @Named(TaskExecutors.SCHEDULED) private ScheduledExecutorService scheduledExecutorService;

    /**
     * The default max inactive interval in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXINACTIVEINTERVAL_SECONDS = 30;

    private Duration maxInactiveInterval = Duration.ofMinutes(DEFAULT_MAXINACTIVEINTERVAL_SECONDS);
    private Integer maxActiveSessions;
    private Integer expiryWait = 0;
    private boolean promptExpiration = false;
    private boolean jdk9plus = false;

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

    public boolean isPromptExpiration() {
        return promptExpiration;
    }

    public void setPromptExpiration(boolean promptExpiration) {
        this.promptExpiration = promptExpiration;
    }

    public boolean isJdk9plus() {
        return jdk9plus;
    }

    public void setJdk9plus(boolean jdk9plus) {
        this.jdk9plus = jdk9plus;
    }

    public Integer getExpiryWait() {
        return expiryWait;
    }

    public void setExpiryWait(Integer expiryWait) {
        this.expiryWait = expiryWait;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }
}
