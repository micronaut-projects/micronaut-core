package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import jakarta.inject.Singleton;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "TenantRouteSourceTest")
// tag::clazz[]
@Singleton
public class TenantRouteSource implements RouteSource { // <1>
    private final RouteTableFactory tables;
    private volatile RouteTable current = RouteTable.empty();

    TenantRouteSource(RouteTableFactory tables) {
        this.tables = tables;
    }

    /**
     * Publish the routes of the tenants.
     *
     * @param tenants The tenants
     */
    public void publish(List<String> tenants) {
        current = tables.buildHttpRoutes(routes -> { // <2>
            for (String tenant : tenants) {
                routes.path("/tenants/" + tenant, group -> {
                    group.after((request, response) -> response.header("X-Tenant", tenant));
                    group.GET("/home", (request, pathVariables) ->
                        HttpResponse.ok("home of " + tenant).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
            }
        });
    }

    @Override
    public RouteTable snapshot() { // <3>
        return current;
    }
}
// end::clazz[]
