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
package io.micronaut.http.server.hostresolver;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * @author Sergio del Amo
 * @since 1.2.0
 */
@Requires(property = HttpHeaderHostResolverConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(HttpHeaderHostResolverConfigurationProperties.PREFIX)
public class HttpHeaderHostResolverConfigurationProperties implements HttpHeaderHostResolverConfiguration {

    public static final String PREFIX = "micronaut.server.host-resolver";
    public static final String DEFAULT_PROTOCOL_HEADER_NAME = "X-Forwarded-For";
    public static final String DEFAULT_HOST_HEADER_NAME = "Host";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private String hostHeaderName = DEFAULT_HOST_HEADER_NAME;
    private String protocolHeaderName = DEFAULT_PROTOCOL_HEADER_NAME;

    /**
     * The HTTP Header name used to resolve Host. Default value ({@value #DEFAULT_HOST_HEADER_NAME}).
     * @param hostHeaderName The HTTP Header name used to resolve Host.
     */
    public void setHostHeaderName(String hostHeaderName) {
        this.hostHeaderName = hostHeaderName;
    }

    /**
     * The HTTP Header name used to resolve Host. Default value ({@value #DEFAULT_PROTOCOL_HEADER_NAME}).
     * @param protocolHeaderName The HTTP Header name used to resolve Host.
     */
    public void setProtocolHeaderName(String protocolHeaderName) {
        this.protocolHeaderName = protocolHeaderName;
    }

    @Nonnull
    @Override
    public String getHostHeaderName() {
        return hostHeaderName;
    }

    @Nonnull
    @Override
    public String getProtocolHeaderName() {
        return protocolHeaderName;
    }
}
