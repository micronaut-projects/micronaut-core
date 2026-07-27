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
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;

/**
 * Configuration for the Netty HTTP client.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(NettyHttpClientConfiguration.NAME)
@Replaces(DefaultHttpClientConfiguration.class)
@BootstrapContextCompatible
@Primary
public class NettyHttpClientConfiguration extends DefaultHttpClientConfiguration {

    /**
     * The Netty HTTP client configuration name.
     */
    public static final String NAME = "netty";

    /**
     * Prefix for Netty HTTP client settings.
     */
    public static final String PREFIX = DefaultHttpClientConfiguration.PREFIX + "." + NAME;

    /**
     * Whether the Netty HTTP client is enabled.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    private boolean nettyEnabled = true;

    /**
     * Creates the Netty HTTP client configuration.
     *
     * @param connectionPoolConfiguration The connection pool configuration.
     * @param webSocketCompressionConfiguration The WebSocket compression configuration.
     * @param http2Configuration The HTTP/2 configuration.
     * @param applicationConfiguration The application configuration.
     */
    @Inject
    public NettyHttpClientConfiguration(DefaultConnectionPoolConfiguration connectionPoolConfiguration,
                                        DefaultWebSocketCompressionConfiguration webSocketCompressionConfiguration,
                                        DefaultHttp2ClientConfiguration http2Configuration,
                                        ApplicationConfiguration applicationConfiguration) {
        super(connectionPoolConfiguration, webSocketCompressionConfiguration, http2Configuration, applicationConfiguration);
    }

    /**
     * Returns whether the Netty HTTP client is enabled.
     *
     * @return Whether the Netty HTTP client is enabled.
     */
    public boolean isEnabled() {
        return nettyEnabled;
    }

    /**
     * Sets whether the Netty HTTP client is enabled.
     *
     * @param enabled Whether the Netty HTTP client is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.nettyEnabled = enabled;
    }
}
