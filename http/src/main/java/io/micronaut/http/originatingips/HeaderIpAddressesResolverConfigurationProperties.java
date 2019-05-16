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
package io.micronaut.http.originatingips;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * {@link ConfigurationProperties} implementation of {@link HeaderIpAddressesResolverConfiguration}.
 *
 * @see <a href="https://en.wikipedia.org/wiki/X-Forwarded-For">X-Forwarded-For</a>
 * @author Sergio del Amo
 * @since 1.2.0
 */
@Requires(property = HeaderIpAddressesResolverConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(HeaderIpAddressesResolverConfigurationProperties.PREFIX)
public class HeaderIpAddressesResolverConfigurationProperties implements HeaderIpAddressesResolverConfiguration {
    public static final String PREFIX = IpAddressesResolver.PREFIX + ".header";
    public static final String DEFAULT_HEADER_NAME = "X-Forwarded-For";
    public static final String DEFAULT_DELIMITER = ",";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private String headerName = DEFAULT_HEADER_NAME;
    private String delimiter = DEFAULT_DELIMITER;
    private boolean enabled = DEFAULT_ENABLED;

    @Override
    @Nonnull
    public String getHeaderName() {
        return headerName;
    }

    /**
     * The HTTP Header name. Default value ({@value #DEFAULT_HEADER_NAME}).
     * @param headerName HTTP Header name.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    @Nonnull
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter used to separate IPs in the Http Header value. Default value ({@value #DEFAULT_DELIMITER})
     * @param delimiter The delimiter used to separate IPs in the Http Header value.
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    /**
     * @return Whether the HTTP Header IpAddresses Resolver is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     *  Whether the HTTP Header IpAddresses Resolver is enabled. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Whether the HTTP Header IpAddresses Resolver is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
