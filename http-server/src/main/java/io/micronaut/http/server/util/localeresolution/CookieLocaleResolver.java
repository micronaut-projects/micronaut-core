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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the Locale from a Cookie within a HTTP Request.
 *
 * @author Sergio del Amo
 * @author James Kleeh
 * @since 2.3.0
 */
@Singleton
@Requires(property = HttpServerConfiguration.HttpLocaleResolutionConfigurationProperties.PREFIX + ".cookie-name")
public class CookieLocaleResolver extends HttpAbstractLocaleResolver {

    private final String cookieName;

    /**
     * @param httpLocaleResolutionConfiguration Locale Resolution configuration for HTTP Requests
     */
    public CookieLocaleResolver(HttpLocaleResolutionConfiguration httpLocaleResolutionConfiguration) {
        super(httpLocaleResolutionConfiguration);
        this.cookieName = httpLocaleResolutionConfiguration.getCookieName()
                .orElseThrow(() -> new IllegalArgumentException("The locale cookie name must be set"));
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return request.getCookies().get(cookieName, Locale.class);
    }
}
