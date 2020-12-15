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
package io.micronaut.http.server.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Singleton
@Requires(property = HttpServerConfiguration.LocaleResolutionConfiguration.PREFIX + ".cookie-name")
public class CookieLocaleResolver implements HttpLocaleResolver {

    public static final Integer ORDER = RequestLocaleResolver.ORDER - 50;

    private final String cookieName;
    private final Locale defaultLocale;

    public CookieLocaleResolver(HttpServerConfiguration serverConfiguration) {
        HttpServerConfiguration.LocaleResolutionConfiguration resolutionConfiguration = Objects.requireNonNull(serverConfiguration.getLocaleResolution());

        this.cookieName = resolutionConfiguration.getCookieName()
                .orElseThrow(() -> new IllegalArgumentException("The locale cookie name must be set"));
        this.defaultLocale = resolutionConfiguration.getDefaultLocale();
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return request.getCookies().get(cookieName, Locale.class);
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return resolve(request).orElse(defaultLocale);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
