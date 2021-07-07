package io.micronaut.management.endpoint.management;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

@Endpoint(ManagementEndpoint.ENDPOINT_ID)
public class ManagementEndpoint {

    static final String ENDPOINT_ID = "management";

    private final ManagementRoutesResolver managementRoutesResolver;
    private final ManagementLinkCollector managementLinkCollector;

    public ManagementEndpoint(ManagementRoutesResolver managementRoutesResolver,
                              ManagementLinkCollector managementLinkCollector) {
        this.managementRoutesResolver = managementRoutesResolver;
        this.managementLinkCollector = managementLinkCollector;
    }

    @Read
    public Resource getManagementRoutes() {
        Stream<UriRoute> uriRoutes = managementRoutesResolver.getRoutes();
        return managementLinkCollector.collectLinks(uriRoutes, ENDPOINT_ID);
    }
}
