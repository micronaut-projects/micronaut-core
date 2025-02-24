/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.runtime.graceful;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import java.time.Duration;

/**
 * Configuration for graceful shutdown.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@ConfigurationProperties(GracefulShutdownConfiguration.PREFIX)
@Requires(property = GracefulShutdownConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public final class GracefulShutdownConfiguration implements Toggleable {
    public static final String PREFIX = "micronaut.lifecycle.graceful-shutdown";
    public static final String ENABLED = PREFIX + ".enabled";

    private boolean enabled;
    @NonNull
    private Duration gracePeriod = Duration.ofSeconds(15);

    /**
     * Whether to enable graceful shutdown on normal shutdown. Off by default.
     *
     * @return {@code true} to enable graceful shutdown
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether to enable graceful shutdown on normal shutdown. Off by default.
     *
     * @param enabled {@code true} to enable graceful shutdown
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Duration to wait until forcing a shutdown.
     *
     * @return The maximum graceful shutdown duration
     */
    @NonNull
    public Duration getGracePeriod() {
        return gracePeriod;
    }

    /**
     * Duration to wait until forcing a shutdown.
     *
     * @param gracePeriod The maximum graceful shutdown duration
     */
    public void setGracePeriod(@NonNull Duration gracePeriod) {
        this.gracePeriod = gracePeriod;
    }
}
