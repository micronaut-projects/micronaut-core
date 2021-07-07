package io.micronaut.management.endpoint.management;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.management.endpoint.management.impl.ManagementResponse;
import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

/**
 * <p>Collect routes in a {@link Resource} to respond in {@link ManagementEndpoint}.</p>
 *
 * @author Hernán Cervera
 * @since 2.5
 * */
public interface ManagementLinkCollector {
    Resource collectLinks(Stream<UriRoute> routes, String selfEndpointId);
}
