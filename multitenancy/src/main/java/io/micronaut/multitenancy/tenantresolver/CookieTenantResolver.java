/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.multitenancy.exceptions.TenantNotFoundException;

import javax.inject.Singleton;
import java.io.Serializable;
import java.util.Optional;

/**
 * A {@link TenantResolver} that resolves the tenant from a request cookie.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Singleton
@Requires(beans = CookieTenantResolverConfiguration.class)
@Requires(property = CookieTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class CookieTenantResolver implements TenantResolver {

    /**
     * The name of the header.
     */
    protected String cookiename = CookieTenantResolverConfiguration.DEFAULT_COOKIENAME;

    /**
     *
     * @param configuration {@link CookieTenantResolver} configuration
     */
    public CookieTenantResolver(CookieTenantResolverConfiguration configuration) {
        if (configuration != null) {
            this.cookiename = configuration.getCookiename();
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

    /**
     *
     * @param request The HTTP request
     * @return the tenant ID if resolved.
     * @throws TenantNotFoundException A exception thrown if the tenant could not be resolved.
     */
    protected Serializable resolveTenantIdentifierAtRequest(HttpRequest<Object> request) throws TenantNotFoundException {
        if (request.getCookies() != null) {
            Optional<Cookie> optionalTenantId = request.getCookies().findCookie(cookiename);
            if (optionalTenantId.isPresent()) {
                return optionalTenantId.get().getValue();
            }
        }
        throw new TenantNotFoundException("Tenant could not be resolved from the Cookie: " + cookiename);
    }
}
