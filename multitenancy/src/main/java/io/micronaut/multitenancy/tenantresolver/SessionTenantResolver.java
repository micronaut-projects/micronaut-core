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
import io.micronaut.session.Session;
import io.micronaut.session.http.HttpSessionFilter;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Optional;

/**
 * Resolves the tenant id from the user HTTP session.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Requires(classes = {Session.class, HttpSessionFilter.class})
@Requires(beans = SessionTenantResolverConfiguration.class)
@Requires(property = SessionTenantResolverConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Singleton
public class SessionTenantResolver implements TenantResolver, HttpRequestTenantResolver {

    /**
     * The name of the session attribute.
     */
    private final String attribute;

    /**
     *
     * @param configuration {@link io.micronaut.multitenancy.tenantresolver.SessionTenantResolverConfiguration} configuration.
     */
    public SessionTenantResolver(SessionTenantResolverConfiguration configuration) {
        this.attribute = (configuration != null) ? configuration.getAttribute() : SessionTenantResolverConfiguration.DEFAULT_ATTRIBUTE;
    }

    @Override
    public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
        return current.map(this::resolveTenantIdentifierAtRequest).orElseThrow(() -> new TenantNotFoundException("Tenant could not be resolved outside a web request"));
    }

    /**
     *
     * @param request The HTTP request
     * @return the tenant ID if resolved.
     * @deprecated Use {@link SessionTenantResolver#resolveTenantIdentifier(HttpRequest)} instead;
     * @throws TenantNotFoundException if tenant not found
     */
    @Deprecated
    protected Serializable resolveTenantIdentifierAtRequest(HttpRequest<Object> request) throws TenantNotFoundException {
        return resolveTenantIdentifier(request);
    }

    @Override
    @NonNull
    public Serializable resolveTenantIdentifier(@NonNull @NotNull HttpRequest<?> request) throws TenantNotFoundException {
        if (this.attribute == null) {
            throw new TenantNotFoundException("Tenant could not be resolved from HTTP Session, because session attribute name is not set");
        }

        Optional<Session> opt = request.getAttributes().get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
        if (!opt.isPresent()) {
            throw new TenantNotFoundException("Tenant could not be resolved from HTTP Session, if session not present");
        }
        Session session = opt.get();
        Optional<Object> tenantId = session.get(attribute);
        if (!tenantId.isPresent()) {
            throw new TenantNotFoundException("Tenant could not be resolved from HTTP Session, if session attribute (" + attribute + ") not present");
        }
        if (!(tenantId.get() instanceof Serializable)) {
            throw new TenantNotFoundException("Tenant was resolved from HTTP Session, but it is not serializable");
        }
        return (Serializable) tenantId.get();
    }

}
