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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.inject.Singleton;
import java.io.Serializable;
import java.util.Optional;

/**
 * A tenant resolver that resolves the tenant from the Subdomain.
 *
 * @author Sergio del Amo
 * @since 6.0
 */
@Singleton
@Requires(property = SubdomainTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class SubdomainTenantResolver implements TenantResolver {

    @Override
    public Serializable resolveTenantIdentifier() {
        Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
        return current.map(this::resolveTenantIdentifierAtRequest).orElseThrow(() -> new TenantNotFoundException("Tenant could not be resolved outside a web request"));
    }

    /**
     *
     * @param request The HTTP request
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException if tenant not found
     */
    protected Serializable resolveTenantIdentifierAtRequest(HttpRequest<Object> request) throws TenantNotFoundException {
        if (request.getHeaders() != null) {
            String host = request.getHeaders().get(HttpHeaders.HOST);
            if (host != null) {
                if (host.contains("/")) {
                    host = host.substring(host.indexOf("/") + 2);
                }
                if (host.contains(".")) {
                    return host.substring(0, host.indexOf("."));
                }
            }
            return TenantResolver.DEFAULT;
        }
        return TenantResolver.DEFAULT;
    }
}
