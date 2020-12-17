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
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link Primary} {@link HttpLocaleResolver} which evaluates every {@link HttpLocaleResolver} by order to resolve a {@link java.util.Locale}.
 *
 * @author Sergio del Amo
 * @author James Kleeh
 * @since 2.3.0
 */
@Singleton
@Primary
public class CompositeHttpLocaleResolver extends HttpDefaultLocaleResolver {

    private final HttpLocaleResolver[] localeResolvers;

    /**
     * @param localeResolvers HTTP Locale Resolvers
     * @param httpLocaleResolutionConfiguration Locale Resolution configuration for HTTP Requests
     */
    public CompositeHttpLocaleResolver(HttpLocaleResolver[] localeResolvers,
                                       HttpLocaleResolutionConfiguration httpLocaleResolutionConfiguration) {
        super(httpLocaleResolutionConfiguration);
        OrderUtil.sort(localeResolvers);
        this.localeResolvers = localeResolvers;
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return Arrays.stream(localeResolvers)
                .map(resolver -> resolver.resolve(request))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
