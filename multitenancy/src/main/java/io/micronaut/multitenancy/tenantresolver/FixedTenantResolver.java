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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.inject.Singleton;
import java.io.Serializable;

/**
 * A {@link io.micronaut.multitenancy.tenantresolver.TenantResolver} that resolves to a fixed static named tenant id.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Requires(beans = FixedTenantResolverConfiguration.class)
@Requires(property = FixedTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Singleton
public class FixedTenantResolver implements TenantResolver {

    private final FixedTenantResolverConfiguration fixedTenantResolverConfiguration;

    /**
     * Constructs a fixed tenant resolver.
     * @param fixedTenantResolverConfiguration Fixed tenant resolver configuration {@link io.micronaut.multitenancy.tenantresolver.FixedTenantResolverConfiguration}.
     */
    public FixedTenantResolver(FixedTenantResolverConfiguration fixedTenantResolverConfiguration) {
        this.fixedTenantResolverConfiguration = fixedTenantResolverConfiguration;
    }

    /**
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException if tenant not found
     */
    @Override
    public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        final Serializable tenantId = fixedTenantResolverConfiguration.getTenantId();

        if (tenantId == null) {
            throw new TenantNotFoundException("TenantId could not be resolved. tenantId is null at FixedTenantResolver");
        }
        return tenantId;
    }
}
