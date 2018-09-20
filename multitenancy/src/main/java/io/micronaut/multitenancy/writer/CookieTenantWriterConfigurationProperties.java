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

package io.micronaut.multitenancy.writer;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 *  {@link io.micronaut.context.annotation.ConfigurationProperties} implementation of {@link io.micronaut.multitenancy.writer.CookieTenantWriterConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(CookieTenantWriterConfigurationProperties.PREFIX)
public class CookieTenantWriterConfigurationProperties implements CookieTenantWriterConfiguration {
    public static final String PREFIX = TenantWriter.PREFIX + ".cookie";
    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default header name.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_COOKIENAME = "tenantId";

    private String cookiename = DEFAULT_COOKIENAME;
    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.multitenancy.writer.CookieTenantWriter}. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Cookie Name. Default value ({@value #DEFAULT_COOKIENAME}).
     * @param cookiename Cookie name
     */
    public void setCookiename(String cookiename) {
        this.cookiename = cookiename;
    }

    /**
     *
     * @return an HTTP Header name. e.g. Authorization
     */
    @Override
    public String getCookiename() {
        return this.cookiename;
    }
}
