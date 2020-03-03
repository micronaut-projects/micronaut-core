/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.multitenancy.tenantresolver;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * {@link io.micronaut.context.annotation.ConfigurationProperties} properties implementation of {@link SystemPropertyTenantResolverConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@ConfigurationProperties(SystemPropertyTenantResolverConfigurationProperties.PREFIX)
public class SystemPropertyTenantResolverConfigurationProperties implements SystemPropertyTenantResolverConfiguration {

    public static final String PREFIX = TenantResolver.PREFIX + ".systemproperty";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLE = false;

    public static final String DEFAULT_SYSTEM_PROPERTY_NAME = "tenantId";

    /**
     * The default enable is {@value #DEFAULT_ENABLE}.
     */
    private boolean enabled = DEFAULT_ENABLE;

    /**
     * The default propertyName is {@value #DEFAULT_SYSTEM_PROPERTY_NAME}.
     */
    private String propertyName = DEFAULT_SYSTEM_PROPERTY_NAME;

    /**
     * System property name. Default value ({@value #DEFAULT_SYSTEM_PROPERTY_NAME}).
     * @param propertyName System property name.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable {@link io.micronaut.multitenancy.tenantresolver.SystemPropertyTenantResolver}. Default value ({@value #DEFAULT_ENABLE}).
     * @param enabled true or false
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getSystemPropertyName() {
        return propertyName;
    }
}
