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
import io.micronaut.http.cookie.Cookie;

import javax.inject.Singleton;
import java.io.Serializable;
import java.time.temporal.ChronoUnit;

/**
 *  Writes the tenantId to in a cookie.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = CookieTenantWriterConfigurationProperties.PREFIX + ".enabled")
@Requires(beans = {CookieTenantWriterConfiguration.class})
public class CookieTenantWriter implements TenantWriter {
    protected final CookieTenantWriterConfiguration cookieTenantWriterConfiguration;

    /**
     *
     * @param cookieTenantWriterConfiguration The {@link CookieTenantWriter} configuration
     */
    public CookieTenantWriter(CookieTenantWriterConfiguration cookieTenantWriterConfiguration) {
        this.cookieTenantWriterConfiguration = cookieTenantWriterConfiguration;
    }

    /**
     * Writes the Tenant Id in a cookie of the request.
     * @param request The {@link MutableHttpRequest} instance
     * @param tenant Tenant Id
     */
    @Override
    public void writeTenant(MutableHttpRequest<?> request, Serializable tenant) {

        if (tenant instanceof String) {
            Cookie cookie = Cookie.of(
                    cookieTenantWriterConfiguration.getCookiename(),
                    (String) tenant
            );
            cookie.configure(cookieTenantWriterConfiguration, request.isSecure());
            if (cookieTenantWriterConfiguration.getCookieMaxAge().isPresent()) {
                cookie.maxAge(cookieTenantWriterConfiguration.getCookieMaxAge().get().get(ChronoUnit.SECONDS));
            } else {
                cookie.maxAge(Integer.MAX_VALUE);
            }
            request.cookie(cookie);
        }
    }
}
