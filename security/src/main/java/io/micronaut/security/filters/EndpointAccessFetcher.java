package io.micronaut.security.filters;

import io.micronaut.core.util.PathMatcher;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.InterceptUrlMapPattern;
import javax.inject.Singleton;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Allows you to find the Access restrictions which apply to an endpoint identified by a request.
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class EndpointAccessFetcher {

    protected final Collection<EndpointAccessProvider> endpointAccessProviders;

    /**
     *
     * @param endpointAccessProviders The collection of EndpointAccessProvider in the context
     */
    public EndpointAccessFetcher(Collection<EndpointAccessProvider> endpointAccessProviders) {
        this.endpointAccessProviders = endpointAccessProviders;
    }

    /**
     *
     * @param request HttpRequest
     * @return the List of {@link InterceptUrlMapPattern} which apply for an HTTP Request.
     */
    public List<InterceptUrlMapPattern> findAllPatternsForRequest(HttpRequest<?> request) {
        List<InterceptUrlMapPattern> endpointsInterceptUrlMappings = new ArrayList<>();
        for ( EndpointAccessProvider endpointAccessProvider : endpointAccessProviders ) {
            if ( endpointAccessProvider.findEndpointAccessRestrictions().isPresent() ) {
                endpointsInterceptUrlMappings.addAll(endpointAccessProvider.findEndpointAccessRestrictions().get());
            }
        }
        return patternsForRequest(request, endpointsInterceptUrlMappings);
    }

    /**
     * Filters a List of {@link InterceptUrlMapPattern} which apply of this particular request.
     * @param request HTTP Request
     * @param interceptUrlMap List of {@link InterceptUrlMapPattern}
     * @return a List of {@link InterceptUrlMapPattern}
     */
    private List<InterceptUrlMapPattern> patternsForRequest(HttpRequest<?> request, List<InterceptUrlMapPattern> interceptUrlMap) {
        final URI uri = request.getUri();
        final String uriString = uri.toString();
        final HttpMethod httpMethod = request.getMethod();
        return interceptUrlMap
                .stream()
                .filter(p ->
                        p.getHttpMethod().equals(httpMethod) &&
                                PathMatcher.ANT.matches(p.getPattern(), uriString))
                .collect(Collectors.toList());
    }
}
