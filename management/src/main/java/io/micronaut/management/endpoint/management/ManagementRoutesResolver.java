package io.micronaut.management.endpoint.management;

import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

public interface ManagementRoutesResolver {
    Stream<UriRoute> getRoutes();
}
