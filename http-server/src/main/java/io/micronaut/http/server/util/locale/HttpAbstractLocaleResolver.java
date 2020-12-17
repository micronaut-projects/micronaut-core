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
package io.micronaut.http.server.util.locale;

import io.micronaut.core.util.locale.AbstractLocaleResolver;
import io.micronaut.http.HttpRequest;

/**
 * Provides an abstract class which implements {@link io.micronaut.core.util.LocaleResolver} and handles default locale resolution.
 *
 * @author Sergio del Amo
 * @since 2.3.0
 */
public abstract class HttpAbstractLocaleResolver extends AbstractLocaleResolver<HttpRequest<?>> implements HttpLocaleResolver {
    public static final Integer ORDER = 50;

    protected HttpLocaleResolutionConfiguration httpLocaleResolutionConfiguration;

    /**
     * @param httpLocaleResolutionConfiguration Locale Resolution configuration for HTTP Requests
     */
    public HttpAbstractLocaleResolver(HttpLocaleResolutionConfiguration httpLocaleResolutionConfiguration) {
        super(httpLocaleResolutionConfiguration.getDefaultLocale());
        this.httpLocaleResolutionConfiguration = httpLocaleResolutionConfiguration;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
