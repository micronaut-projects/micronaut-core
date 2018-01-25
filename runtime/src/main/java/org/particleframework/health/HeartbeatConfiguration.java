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
package org.particleframework.health;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.util.Toggleable;

import java.time.Duration;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("particle.heartbeat")
public class HeartbeatConfiguration implements Toggleable {

    private Duration interval = Duration.ofSeconds(15);
    private boolean enabled = false;

    /**
     * @return The interval with which to publish {@link HeartbeatEvent} instances
     */
    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    /**
     * @return Whether {@link HeartbeatEvent} instances should be published in a background thread
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
