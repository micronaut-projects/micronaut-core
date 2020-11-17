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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * An interface for multi-tenant aware applications which resolve the current identifier for the current request.
 *
 * @author Sergio del Amo
 * @since 2.1.2
 */
@FunctionalInterface
public interface HttpRequestTenantResolver {
    /**
     * Resolves the current tenant identifier.
     * @param request The HTTP request
     * @return The tenant identifier
     * @throws TenantNotFoundException if tenant not found
     */
    @NonNull
    Serializable resolveTenantIdentifier(@NonNull @NotNull HttpRequest<?> request) throws TenantNotFoundException;
}
