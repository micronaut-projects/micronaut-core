package io.micronaut.web.router.version;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.version.strategy.VersionExtractingStrategy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link RouteMatchesFilter} responsible for filtering route matches on {@link Version}.
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
public class VersioningRouteMatchesFilter implements RouteMatchesFilter {

    private final List<VersionExtractingStrategy> resolvingStrategies;

    /**
     * Creates a {@link VersioningRouteMatchesFilter} with a collection of {@link VersionExtractingStrategy}.
     * @param resolvingStrategies A list of {@link VersionExtractingStrategy} beans to extract version from HTTP request
     */
    @Inject
    public VersioningRouteMatchesFilter(List<VersionExtractingStrategy> resolvingStrategies) {
        this.resolvingStrategies = resolvingStrategies;
    }

    /**
     * Filters route matches by specified version.
     *
     * @param matches The list of {@link UriRouteMatch}
     * @param request The HTTP request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return A filtered list of route matches
     */
    @Override
    public <T, R> List<UriRouteMatch<T, R>> filter(List<UriRouteMatch<T, R>> matches, HttpRequest<?> request) {

        Objects.requireNonNull(matches, "'matches' list should be not null");
        Objects.requireNonNull(request, "'request' should be not null");

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
