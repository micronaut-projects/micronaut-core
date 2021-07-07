package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementController;
import io.micronaut.management.endpoint.management.ManagementRoutesResolver;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;

import jakarta.inject.Singleton;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <p>Default {@link ManagementRoutesResolver} implementation.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Singleton
@Requires(beans = ManagementController.class)
public class DefaultManagementRoutesResolver implements ManagementRoutesResolver {

    private final Router router;

    public DefaultManagementRoutesResolver(Router router) {
        this.router = router;
    }

    /**
     * <p>Find routes which are backed by a method and which owning class
     * is marked with {@link Endpoint}</p>
     *
     * @return The {@link UriRoute}s in a stream.
     */
    @Override
    public Stream<UriRoute> getRoutes() {
        return router.uriRoutes().filter(this::isManagementRoute);
    }

    private boolean isManagementRoute(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            AnnotationValue<Endpoint> endpoint = ((MethodBasedRoute) route).getTargetMethod()
                    .getAnnotationMetadata().getAnnotation(Endpoint.class);
            return Objects.nonNull(endpoint);
        }
        return false;
    }
}
