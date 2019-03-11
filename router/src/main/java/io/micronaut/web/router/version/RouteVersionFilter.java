/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.web.router.version;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.filter.RouteMatchFilter;
import io.micronaut.web.router.version.resolution.RequestVersionResolver;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Implementation of {@link RouteMatchFilter} responsible for filtering route matches on {@link Version}.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
public class RouteVersionFilter implements RouteMatchFilter {

    private final List<RequestVersionResolver> resolvingStrategies;

    /**
     * Creates a {@link RouteVersionFilter} with a collection of {@link RequestVersionResolver}.
     *
     * @param resolvingStrategies A list of {@link RequestVersionResolver} beans to extract version from HTTP request
     */
    @Inject
    public RouteVersionFilter(List<RequestVersionResolver> resolvingStrategies) {
        this.resolvingStrategies = resolvingStrategies;
    }

    /**
     * Filters route matches by specified version.
     *
     * @param <T>     The target type
     * @param <R>     The return type
     * @param request The HTTP request
     * @return A filtered list of route matches
     */
    @Override
    public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {

        ArgumentUtils.requireNonNull("request", request);

        if (resolvingStrategies == null || resolvingStrategies.isEmpty()) {
            return (match) -> true;
        }

        return (match) -> resolvingStrategies.stream()
                .map(strategy -> strategy.resolve(request))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(opt -> opt.map(v -> isVersionMatched(match, v)))
                .orElse(true);
    }

    private <T, R> boolean isVersionMatched(UriRouteMatch<T, R> routeMatch, String version) {
        return Optional.ofNullable(routeMatch.getExecutableMethod().getAnnotation(Version.class))
                .flatMap(annotation -> annotation.getValue(String.class))
                .filter(specifiedVersion -> specifiedVersion.equals(version))
                .isPresent();
    }

}
