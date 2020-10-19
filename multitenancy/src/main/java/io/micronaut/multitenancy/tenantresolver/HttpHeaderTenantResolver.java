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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Optional;

/**
 * A {@link io.micronaut.multitenancy.tenantresolver.TenantResolver} that resolves the tenant from the request HTTP Header.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Singleton
@Requires(beans = HttpHeaderTenantResolverConfiguration.class)
@Requires(property = HttpHeaderTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class HttpHeaderTenantResolver implements TenantResolver, HttpRequestTenantResolver {

    /**
     * The name of the header.
     */
    protected String headerName = HttpHeaderTenantResolverConfiguration.DEFAULT_HEADER_NAME;

    /**
     *
     * @param configuration {@link HttpHeaderTenantResolverConfiguration} configuration
     */
    public HttpHeaderTenantResolver(HttpHeaderTenantResolverConfiguration configuration) {
        if (configuration != null) {
            this.headerName = configuration.getHeaderName();
        }
    }

    /**
     *
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException if tenant not found
     */
    @Override
    public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
        return current.map(this::resolveTenantIdentifierAtRequest).orElseThrow(() -> new TenantNotFoundException("Tenant could not be resolved outside a web request"));
    }

    @Override
    public Serializable resolveTenantIdentifier(@NonNull @NotNull HttpRequest<?> request) throws TenantNotFoundException {
        String tenantId = request.getHeaders().get(headerName);
        if (tenantId == null) {
            throw new TenantNotFoundException("Tenant could not be resolved. Header " + headerName + " value is null");
        }
        return tenantId;
    }

    /**
     *
     * @param request The HTTP request
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException A exception thrown if the tenant could not be resolved.
     * @deprecated Use {@link HttpHeaderTenantResolver#resolveTenantIdentifier(HttpRequest)} instead.
     */
    @Deprecated
    protected Serializable resolveTenantIdentifierAtRequest(HttpRequest<Object> request) throws TenantNotFoundException {
        return resolveTenantIdentifier(request);
    }
}
