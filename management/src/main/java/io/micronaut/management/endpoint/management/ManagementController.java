package io.micronaut.management.endpoint.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.UriRoute;
import io.reactivex.Single;

import java.util.stream.Stream;

/**
 * <p>Exposes the available management endpoints of the application</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Controller
@Requires(beans = EmbeddedServer.class)
public class ManagementController {

    private final HttpHostResolver httpHostResolver;

    private final ManagementRoutesResolver managementRoutesResolver;
    private final ManagementDataCollector<?> managementDataCollector;

    public ManagementController(HttpHostResolver httpHostResolver,
                                ManagementRoutesResolver managementRoutesResolver,
                                ManagementDataCollector<?> managementDataCollector) {
        this.httpHostResolver = httpHostResolver;
        this.managementRoutesResolver = managementRoutesResolver;
        this.managementDataCollector = managementDataCollector;
    }

    @Get("management")
    public Single<?> getManagementRoutes(HttpRequest<?> httpRequest) {
        String routeBase = httpHostResolver.resolve(httpRequest);
        String managementDiscoveryPath = httpRequest.getPath();
        Stream<UriRoute> uriRoutes = managementRoutesResolver.getRoutes();

        return Single.fromPublisher(managementDataCollector.collectData(uriRoutes, routeBase,
                managementDiscoveryPath, false));
    }
}
