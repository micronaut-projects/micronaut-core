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
@ConfigurationProperties("micronaut.heartbeat")
public class HeartbeatConfiguration implements Toggleable {

    private Duration interval = Duration.ofSeconds(15);
    private boolean enabled = true;

    /**
     * @return The interval with which to publish {@link HeartbeatEvent} instances
     */
    public Duration getInterval() {
        return interval;
    }

    /**
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
     * @param enabled Enable the publish of {@link HeartbeatEvent} event instances
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
