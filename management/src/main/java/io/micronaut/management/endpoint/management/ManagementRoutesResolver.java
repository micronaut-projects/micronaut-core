package io.micronaut.management.endpoint.management;

import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

/**
 * <p>Find the routes which the {@link ManagementEndpoint} provides.</p>
 *
 * @author Hernán Cervera
 * @since 2.5
 */
public interface ManagementRoutesResolver {
    Stream<UriRoute> getRoutes();
}
