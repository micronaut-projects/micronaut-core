package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementEndpoint;
import io.micronaut.management.endpoint.management.ManagementRoutesResolver;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
@Requires(beans = ManagementEndpoint.class)
public class DefaultManagementRoutesResolver implements ManagementRoutesResolver {

    private final Router router;

    public DefaultManagementRoutesResolver(Router router) {
        this.router = router;
    }

    @Override
    public Stream<UriRoute> getRoutes() {
        return router.uriRoutes().filter(this::isManagementRoute);
    }

    public boolean isManagementRoute(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
            Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
            return Objects.nonNull(endpoint);
        }

        return false;
    }
}
