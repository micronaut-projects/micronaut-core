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
package io.micronaut.http.server.util.localeresolution;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.localeresolution.FixedLocaleResolver;
import io.micronaut.core.util.localeresolution.LocaleResolutionConfiguration;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;

/**
 * Generic implementation of {@link io.micronaut.core.util.LocaleResolver} for fixed locale resolution.
 *
 * @author Sergio del Amo
 * @since 2.3.0
 */
@Singleton
@Requires(property = HttpServerConfiguration.HttpLocaleResolutionConfigurationProperties.PREFIX + ".fixed")
public class HttpFixedLocaleResolver extends FixedLocaleResolver<HttpRequest<?>> implements HttpLocaleResolver {

    public static final Integer ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * @param localeResolutionConfiguration Locale Resolution configuration
     */
    public HttpFixedLocaleResolver(LocaleResolutionConfiguration localeResolutionConfiguration) {
        super(localeResolutionConfiguration);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
