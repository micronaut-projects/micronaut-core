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
package io.micronaut.health;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

import java.time.Duration;

/**
 * Configuration for heart beat.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(HeartbeatConfiguration.PREFIX)
public class HeartbeatConfiguration implements Toggleable {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default interval seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_INTERVAL_SECONDS = 15;

    /**
     * The prefix used for the heart beat configuration.
     */
    public static final String PREFIX = "micronaut.heartbeat";

    /**
     * Whether the heartbeat is enabled.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    private Duration interval = Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS);
    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return The interval with which to publish {@link HeartbeatEvent} instances
     */
    public Duration getInterval() {
        return interval;
    }

    /**
     * Default value ({@value #DEFAULT_INTERVAL_SECONDS} seconds).
     * @param interval The interval to publish {@link HeartbeatEvent} instances
     */
    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    /**
     * @return Whether {@link HeartbeatEvent} event instances should be published by the server
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable the publish of {@link HeartbeatEvent} event instances
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
