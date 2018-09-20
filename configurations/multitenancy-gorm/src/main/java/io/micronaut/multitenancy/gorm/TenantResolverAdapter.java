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

package io.micronaut.multitenancy.gorm;

import io.micronaut.multitenancy.exceptions.TenantException;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;
import io.micronaut.multitenancy.tenantresolver.TenantResolver;

import java.io.Serializable;

/**
 * An adapter between GORM {@link org.grails.datastore.mapping.multitenancy.TenantResolver} and Micronaut {@link io.micronaut.multitenancy.tenantresolver.TenantResolver}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
public class TenantResolverAdapter implements org.grails.datastore.mapping.multitenancy.TenantResolver {

    private final TenantResolver tenantResolver;

    /**
     *
     * @param tenantResolver a Micronaut {@link io.micronaut.multitenancy.tenantresolver.TenantResolver}.
     */
    public TenantResolverAdapter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public Serializable resolveTenantIdentifier() throws org.grails.datastore.mapping.multitenancy.exceptions.TenantException {
        try {
            return tenantResolver.resolveTenantIdentifier();
        } catch (TenantException e) {
            if (e instanceof TenantNotFoundException) {
                throw new org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException(e.getMessage(), e.getCause());
            }
            throw new org.grails.datastore.mapping.multitenancy.exceptions.TenantException(e.getMessage(), e.getCause());
        }
    }
}
