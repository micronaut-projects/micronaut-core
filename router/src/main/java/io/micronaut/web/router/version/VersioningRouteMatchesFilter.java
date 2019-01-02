/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.web.router.filter.RouteMatchesFilter;
import io.micronaut.web.router.version.strategy.VersionExtractingStrategy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link RouteMatchesFilter} responsible for filtering route matches on {@link Version}.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
public class VersioningRouteMatchesFilter implements RouteMatchesFilter {

    private final List<VersionExtractingStrategy> resolvingStrategies;

    /**
     * Creates a {@link VersioningRouteMatchesFilter} with a collection of {@link VersionExtractingStrategy}.
     *
     * @param resolvingStrategies A list of {@link VersionExtractingStrategy} beans to extract version from HTTP request
     */
    @Inject
    public VersioningRouteMatchesFilter(List<VersionExtractingStrategy> resolvingStrategies) {
        this.resolvingStrategies = resolvingStrategies;
    }

    /**
     * Filters route matches by specified version.
     *
     * @param <T>     The target type
     * @param <R>     The return type
     * @param request The HTTP request
     * @param matches The list of {@link UriRouteMatch}
     * @return A filtered list of route matches
     */
    @Override
    public <T, R> List<UriRouteMatch<T, R>> filter(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {

        ArgumentUtils.requireNonNull("matches", matches);
        ArgumentUtils.requireNonNull("request", request);

        return resolvingStrategies.stream()
                .map(strategy -> strategy.extract(request))
                .findFirst()
                .filter(Optional::isPresent)
                .flatMap(Function.identity())
                .map(version -> matches.stream()
                        .filter(routeMatch -> isVersionMatched(routeMatch, version))
                        .collect(Collectors.toList()))
                .filter(filteredRoutes -> !filteredRoutes.isEmpty())
                .orElse(matches);
    }

    private <T, R> boolean isVersionMatched(UriRouteMatch<T, R> routeMatch, String version) {
        return Optional.ofNullable(routeMatch.getExecutableMethod().getAnnotation(Version.class))
                .flatMap(annotation -> annotation.getValue(String.class))
                .filter(specifiedVersion -> specifiedVersion.equals(version))
                .isPresent();
    }

}
