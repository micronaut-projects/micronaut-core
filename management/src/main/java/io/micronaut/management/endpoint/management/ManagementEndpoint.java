package io.micronaut.management.endpoint.management;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.hateoas.Link;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.management.impl.NavigableRouteData;
import io.micronaut.management.endpoint.routes.RouteDataCollector;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import java.util.Objects;
import java.util.stream.Stream;

@Endpoint("management")
public class ManagementEndpoint {

    private final Router router;
    private final NavigableRouteData routeData;

    /**
     * @param router The {@link Router}
     */
    public ManagementEndpoint(Router router, NavigableRouteData routeData) {
        this.router = router;
        this.routeData = routeData;
    }

    // TODO: Consider refactoring to use RxJava
    @Read
    public ManagementRoutes getManagementRoutes() {
        Stream<UriRoute> uriRoutes = router.uriRoutes().filter(this::isManagementRoute);
        return buildHateoasResponse(uriRoutes);
    }

    public boolean isManagementRoute(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
            Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
            return Objects.nonNull(endpoint) && route.getHttpMethod() == HttpMethod.GET;
        }
        return false;
    }

    // TODO: Refactor this method somewhere that it makes more sense
    private ManagementRoutes buildHateoasResponse(Stream<UriRoute> uriRoutes) {
        ManagementRoutes routes = new ManagementRoutes();

        // TODO: Set the proper management link only on self
        routes.link(Link.SELF, Link.build("/the-management-link").templated(false).build());

        // TODO: Set the proper value for isTemplated
        uriRoutes.forEach(route ->
                routes.link(getEndpointId(route),
                        Link.build(routeData.getAbsoluteRoute(route)).build()));

        return routes;
    }

    public String getEndpointId(UriRoute route) {
        if (!(route instanceof MethodBasedRoute)) {
            throw new IllegalArgumentException("route should be backed by a method");
        }
        Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
        Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
        return endpoint.value().equals("") ? endpoint.id() : endpoint.value();
    }
}
