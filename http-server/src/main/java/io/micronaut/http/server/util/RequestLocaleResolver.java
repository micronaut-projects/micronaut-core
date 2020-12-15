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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

@Singleton
@Requires(property = HttpServerConfiguration.LocaleResolutionConfiguration.PREFIX + ".header", notEquals = StringUtils.FALSE)
public class RequestLocaleResolver implements HttpLocaleResolver {

    public static final Integer ORDER = 100;

    private final Locale defaultLocale;

    public RequestLocaleResolver(HttpServerConfiguration serverConfiguration) {
        this.defaultLocale = Optional.ofNullable(serverConfiguration.getLocaleResolution())
                .map(HttpServerConfiguration.LocaleResolutionConfiguration::getDefaultLocale)
                .orElse(Locale.getDefault());
    }

        @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return request.getLocale();
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
