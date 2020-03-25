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
 * {@link io.micronaut.context.annotation.ConfigurationProperties} implementation of {@link PrincipalTenantResolverConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@ConfigurationProperties(PrincipalTenantResolverConfigurationProperties.PREFIX)
public class PrincipalTenantResolverConfigurationProperties implements PrincipalTenantResolverConfiguration {

    public static final String PREFIX = TenantResolver.PREFIX + ".principal";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLE = false;

    /**
     * The default enable value.
     */
    private boolean enabled = DEFAULT_ENABLE;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.multitenancy.tenantresolver.PrincipalTenantResolver}. The default enable is {@value #DEFAULT_ENABLE}.
     * @param enabled True or false.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
