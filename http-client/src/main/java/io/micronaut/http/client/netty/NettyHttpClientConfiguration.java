/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.http.client.DefaultHttpClientConfiguration;

/**
 * Configuration for the Netty HTTP client.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(NettyHttpClientConfiguration.PREFIX)
public class NettyHttpClientConfiguration {

    /**
     * Prefix for Netty HTTP client settings.
     */
    public static final String PREFIX = DefaultHttpClientConfiguration.PREFIX + ".netty";

    /**
     * Whether the Netty HTTP client is enabled.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    private boolean enabled = true;

    /**
     * Returns whether the Netty HTTP client is enabled.
     *
     * @return Whether the Netty HTTP client is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the Netty HTTP client is enabled.
     *
     * @param enabled Whether the Netty HTTP client is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
