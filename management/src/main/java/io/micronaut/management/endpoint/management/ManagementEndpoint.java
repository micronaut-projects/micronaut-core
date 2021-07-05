package io.micronaut.management.endpoint.management;

import io.micronaut.http.HttpMethod;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.routes.RouteDataCollector;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import io.reactivex.Single;

import javax.inject.Named;
import java.util.Objects;
import java.util.stream.Stream;

@Endpoint("management")
public class ManagementEndpoint {

    private final Router router;
    private final RouteDataCollector routeDataCollector;

    /**
     * @param router The {@link Router}
     * @param routeDataCollector The {@link RouteDataCollector}
     */
    public ManagementEndpoint(Router router,
                              @Named("ManagementRxJava") RouteDataCollector routeDataCollector) {
        this.router = router;
        this.routeDataCollector = routeDataCollector;
    }

    @Read
    public Single getManagementRoutes() {
        Stream<UriRoute> uriRoutes = router.uriRoutes().filter(this::isManagementRoute);
        return Single.fromPublisher(routeDataCollector.getData(uriRoutes));
    }

    public boolean isManagementRoute(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
            Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
            return Objects.nonNull(endpoint) && route.getHttpMethod() == HttpMethod.GET;
        }
        return false;
    }
}
