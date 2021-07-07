package io.micronaut.management.endpoint.management;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.management.endpoint.management.impl.ManagementResponse;
import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

public interface ManagementLinkCollector {
    Resource collectLinks(Stream<UriRoute> routes, String selfEndpointId);
}
