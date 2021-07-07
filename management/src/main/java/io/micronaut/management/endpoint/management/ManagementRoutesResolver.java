package io.micronaut.management.endpoint.management;

import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

/**
 * <p>Find the routes which are used by the {@link ManagementController}.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
public interface ManagementRoutesResolver {

    /**
     * @return management routes.
     */
    Stream<UriRoute> getRoutes();
}
