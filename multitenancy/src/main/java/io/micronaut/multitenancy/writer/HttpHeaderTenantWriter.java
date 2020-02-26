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
package io.micronaut.multitenancy.writer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MutableHttpRequest;

import javax.inject.Singleton;
import java.io.Serializable;

/**
 *  Write the tenant id in an HTTP header.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = HttpHeaderTenantWriterConfigurationProperties.PREFIX + ".enabled")
@Requires(beans = {HttpHeaderTenantWriterConfiguration.class})
public class HttpHeaderTenantWriter implements TenantWriter {
    protected final HttpHeaderTenantWriterConfiguration httpHeaderTenantWriterConfiguration;

    /**
     *
     * @param httpHeaderTenantWriterConfiguration The {@link HttpHeaderTenantWriter} configuration
     */
    public HttpHeaderTenantWriter(HttpHeaderTenantWriterConfiguration httpHeaderTenantWriterConfiguration) {
        this.httpHeaderTenantWriterConfiguration = httpHeaderTenantWriterConfiguration;
    }

    /**
     *
     * @return the HTTP Header name where the token will be written to
     */
    protected String getHeaderName() {
        return httpHeaderTenantWriterConfiguration.getHeaderName();
    }

    /**
     * Writes the token to the request.
     * @param request The {@link MutableHttpRequest} instance
     * @param tenant Tenant Id
     */
    @Override
    public void writeTenant(MutableHttpRequest<?> request, Serializable tenant) {
        if (tenant instanceof CharSequence) {
            request.header(getHeaderName(), (CharSequence) tenant);
        }
    }
}
