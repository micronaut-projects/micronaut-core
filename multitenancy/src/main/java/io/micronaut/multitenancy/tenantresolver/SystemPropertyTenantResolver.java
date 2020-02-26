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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.inject.Singleton;
import java.io.Serializable;

/**
 * A {@link io.micronaut.multitenancy.tenantresolver.TenantResolver} that resolves from a System property called {@value SystemPropertyTenantResolverConfigurationProperties#DEFAULT_SYSTEM_PROPERTY_NAME}. Useful for testing.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(beans = SystemPropertyTenantResolverConfiguration.class)
@Requires(property = SystemPropertyTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class SystemPropertyTenantResolver implements TenantResolver {

    private SystemPropertyTenantResolverConfiguration systemPropertyTenantResolverConfiguration;

    /**
     * Constructs a system property tenant resolver.
     * @param systemPropertyTenantResolverConfiguration {@link io.micronaut.multitenancy.tenantresolver.SystemPropertyTenantResolverConfiguration} Configuration for system property tenant resolver.
     */
    public SystemPropertyTenantResolver(SystemPropertyTenantResolverConfiguration systemPropertyTenantResolverConfiguration) {
        this.systemPropertyTenantResolverConfiguration = systemPropertyTenantResolverConfiguration;
    }

    /**
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException if tenant not found.
     */
    @Override
    public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        String value = System.getProperty(systemPropertyTenantResolverConfiguration.getSystemPropertyName());
        if (value == null) {
            throw new TenantNotFoundException("System property (" + systemPropertyTenantResolverConfiguration.getSystemPropertyName() + ") not found");
        }
        return value;
    }
}
