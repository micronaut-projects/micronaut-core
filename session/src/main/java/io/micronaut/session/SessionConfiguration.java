/*
 * Copyright 2017-2018 original authors
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

import java.time.Duration;
import java.util.OptionalInt;

/**
 * <p>Base configuration properties for session handling.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(SessionSettings.PREFIX)
public class SessionConfiguration {

    private Duration maxInactiveInterval = Duration.ofMinutes(30);
    private Integer maxActiveSessions;

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
}
