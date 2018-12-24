package io.micronaut.web.router.version;

import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.UriRouteMatch;

import java.util.List;

/**
 * A filter responsible for filtering route matches.
 *
 * @author BogdanOros
 */
public interface RouteMatchesFilter {

    /**
     * A method responsible for filtering route matches based on request.
     *
     * @param matches The list of {@link UriRouteMatch}
     * @param request The HTTP request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return A filtered list of route matches
     */
    <T, R> List<UriRouteMatch<T, R>> filter(List<UriRouteMatch<T, R>> matches, HttpRequest<?> request);

}
