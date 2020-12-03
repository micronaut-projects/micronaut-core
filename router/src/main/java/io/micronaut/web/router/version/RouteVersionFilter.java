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
package io.micronaut.web.router.version;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.filter.RouteMatchFilter;
import io.micronaut.web.router.version.resolution.RequestVersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
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

    private static final Logger LOG = LoggerFactory.getLogger(RouteVersionFilter.class);

    private final List<RequestVersionResolver> resolvingStrategies;
    private final DefaultVersionProvider defaultVersionProvider;

    /**
     * Creates a {@link RouteVersionFilter} with a collection of {@link RequestVersionResolver}.
     *
     * @param resolvingStrategies A list of {@link RequestVersionResolver} beans to extract version from HTTP request
     * @param defaultVersionProvider The Default Version Provider
     */
    public RouteVersionFilter(List<RequestVersionResolver> resolvingStrategies,
                              @Nullable DefaultVersionProvider defaultVersionProvider) {
        this.resolvingStrategies = resolvingStrategies;
        this.defaultVersionProvider = defaultVersionProvider;
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
            return match -> true;
        }

        Optional<String> defaultVersion = defaultVersionProvider == null ? Optional.empty() : Optional.of(defaultVersionProvider.resolveDefaultVersion());

        Optional<String> version = resolveVersion(request);

        return match -> {
            Optional<String> routeVersion = getVersion(match);
            return routeVersion.isPresent() ?
                    matchIfRouteIsVersioned(request, version.orElse(defaultVersion.orElse(null)), routeVersion.get()) :
                    matchIfRouteIsNotVersioned(request, version.orElse(null));
        };
    }

    /**
     *
     * @param request HTTP Request
     * @param version The version resolved evaluating the HTTP Request with beans of type {@link RequestVersionResolver}
     * @return <code>true</code> if no version was resolved from the request
     */
    protected boolean matchIfRouteIsNotVersioned(@NonNull HttpRequest<?> request,
                                                 @Nullable String version) {
        //route is not versioned but request is
        if (version != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Route does not specify a version but the version {} was resolved for request to URI {}", version, request.getUri());
            }
            return false;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Route does not specify a version and no version was resolved for request to URI {}", request.getUri());
        }
        return true;
    }
    
    /**
     *
     * @param request HTTP Request
     * @param resolvedVersion The version resolved evaluating the HTTP Request with beans of type {@link RequestVersionResolver} and the {@link RoutesVersioningConfiguration#getDefaultVersion()}.
     * @param routeVersion The route's version. For example, it could specified by the {@link Version} annotation.
     * @return <code>true</code> if the resolved version matches the route version or if the resolved version is <code>null</code>
     */
    protected boolean matchIfRouteIsVersioned(@NonNull HttpRequest<?> request,
                                              @Nullable String resolvedVersion,
                                              @NonNull String routeVersion) {
        //no version found and no default version configured
        if (resolvedVersion == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Route specifies a version {} and no version information resolved for request to URI {}", routeVersion, request.getUri());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Route specifies a version {} and the version {} was resolved for request to URI {}", routeVersion, resolvedVersion, request.getUri());
        }
        return resolvedVersion.equals(routeVersion);
    }

    /**
     *
     * @param request HTTP Request
     * @return the resolved requested version wrapped in an {@link Optional}
     */
    @NonNull
    protected Optional<String> resolveVersion(@NonNull HttpRequest<?> request) {
        return resolvingStrategies.stream()
                .map(strategy -> strategy.resolve(request).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     *
     * @param routeMatch
     * @param <T> The target type
     * @param <R> The return type
     * @return Returns the value of the annotation {@link Version} in the route method wrapped in an {@link Optional}.
     */
    protected <T, R> Optional<String> getVersion(UriRouteMatch<T, R> routeMatch) {
        return routeMatch.getExecutableMethod().stringValue(Version.class);
    }

}
