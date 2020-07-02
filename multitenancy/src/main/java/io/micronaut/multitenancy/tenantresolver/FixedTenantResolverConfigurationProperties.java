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
package io.micronaut.multitenancy.tenantresolver;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * {@link io.micronaut.context.annotation.ConfigurationProperties} implementation of {@link FixedTenantResolverConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@ConfigurationProperties(FixedTenantResolverConfigurationProperties.PREFIX)
public class FixedTenantResolverConfigurationProperties implements FixedTenantResolverConfiguration {
    public static final String PREFIX = TenantResolver.PREFIX + ".fixed";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLE = false;

    /**
     * The default enable is {@value #DEFAULT_ENABLE}.
     */
    private boolean enabled = DEFAULT_ENABLE;

    private String tenantId = TenantResolver.DEFAULT;

    /**
     * The fixed tenant ID. Default value to ({@value io.micronaut.multitenancy.tenantresolver.TenantResolver#DEFAULT}).
     * @param tenantId the fixed Tenant ID.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.multitenancy.tenantresolver.FixedTenantResolver}. Default value ({@value #DEFAULT_ENABLE}).
     * @param enabled true or false
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
