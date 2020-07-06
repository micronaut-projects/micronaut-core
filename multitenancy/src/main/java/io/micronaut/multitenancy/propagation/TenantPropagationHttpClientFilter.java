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
package io.micronaut.multitenancy.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.util.OutgoingHttpRequestProcessor;
import io.micronaut.multitenancy.tenantresolver.TenantResolver;
import io.micronaut.multitenancy.exceptions.TenantException;
import io.micronaut.multitenancy.writer.TenantWriter;
import org.reactivestreams.Publisher;

import java.io.Serializable;

/**
 * {@link io.micronaut.http.filter.HttpClientFilter} to enable Token propagation.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Filter("${" + TenantPropagationConfigurationProperties.PREFIX + ".path:/**}")
@Requires(beans = {TenantWriter.class, TenantResolver.class, TenantPropagationConfiguration.class, OutgoingHttpRequestProcessor.class})
@Requires(property = TenantPropagationConfigurationProperties.PREFIX + ".enabled")
public class TenantPropagationHttpClientFilter implements HttpClientFilter  {

    protected final TenantPropagationConfiguration tenantPropagationConfiguration;
    protected final TenantWriter tokenWriter;
    protected final TenantResolver tenantResolver;
    protected final OutgoingHttpRequestProcessor outgoingHttpRequestProcessor;

    /**
     * @param tenantResolver bean responsible of resolving the tenant
     * @param tokenWriter bean responsible of writing the token to the target request
     * @param tenantPropagationConfiguration Tenant Propagation configuration
     * @param outgoingHttpRequestProcessor Utility to decide whether to process the request
     */
    public TenantPropagationHttpClientFilter(TenantResolver tenantResolver,
                                             TenantWriter tokenWriter,
                                             TenantPropagationConfiguration tenantPropagationConfiguration,
                                             OutgoingHttpRequestProcessor outgoingHttpRequestProcessor) {
        this.tenantResolver = tenantResolver;
        this.tokenWriter = tokenWriter;
        this.tenantPropagationConfiguration = tenantPropagationConfiguration;
        this.outgoingHttpRequestProcessor = outgoingHttpRequestProcessor;
    }

    /**
     * If the request should be processed and the tenant id is resolved, the tenant is written to the targeted request with a {@link io.micronaut.multitenancy.writer.TenantWriter}.
     * @param targetRequest The target request
     * @param chain The filter chain
     * @return The publisher of the response
     */
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> targetRequest, ClientFilterChain chain) {
        if (!outgoingHttpRequestProcessor.shouldProcessRequest(tenantPropagationConfiguration, targetRequest)) {
            return chain.proceed(targetRequest);
        }
        try {
            Serializable tenantId = tenantResolver.resolveTenantIdentifier();
            tokenWriter.writeTenant(targetRequest, tenantId);
            return chain.proceed(targetRequest);

        } catch (TenantException e) {
            return chain.proceed(targetRequest);
        }
    }
}
