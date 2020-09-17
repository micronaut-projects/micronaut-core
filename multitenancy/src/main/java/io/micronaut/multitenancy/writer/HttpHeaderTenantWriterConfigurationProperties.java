/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.multitenancy.writer;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 *  Tenant Writer Configuration Properties.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(HttpHeaderTenantWriterConfigurationProperties.PREFIX)
public class HttpHeaderTenantWriterConfigurationProperties implements HttpHeaderTenantWriterConfiguration {
    public static final String PREFIX = TenantWriter.PREFIX + ".httpheader";
    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default header name.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_HEADERNAME = "tenantId";

    private String headerName = DEFAULT_HEADERNAME;
    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.multitenancy.writer.HttpHeaderTenantWriter}. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Http Header name. Default value ({@value #DEFAULT_HEADERNAME}).
     * @param headerName HTTP header name
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     *
     * @return an HTTP Header name. e.g. Authorization
     */
    @Override
    public String getHeaderName() {
        return this.headerName;
    }
}
