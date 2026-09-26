package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import jakarta.inject.Singleton;

import java.util.Set;
// end::imports[]

@Requires(property = "spec.name", value = "ConditionRoutesTest")
@Singleton
public class ConditionRoutes implements HttpRoutes {
    private static final Set<String> SHOPS = Set.of("north", "south");

    private final int managementPort;

    ConditionRoutes(@Value("${management.port}") int managementPort) {
        this.managementPort = managementPort;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::where[]
        routes.GET("/search", (request, pathVariables) -> text("beta search"))
            .where(RequestPredicates.any( // <1>
                RequestPredicates.header("X-Beta"),
                RequestPredicates.queryParam("beta", "true")))
            .order(-1); // <2>
        routes.GET("/search", (request, pathVariables) -> text("search")); // <3>
        // end::where[]

        // tag::constrain[]
        routes.path("/shops/{shop}", shop -> {
            shop.constrain("shop", SHOPS); // <1>
            shop.GET("/stock", (request, pathVariables) -> text("stock of " + pathVariables.getString("shop")));
        });
        routes.GET("/items/{id}", (request, pathVariables) -> text("item " + pathVariables.getLong("id")))
            .constrain("id", Long.class, id -> id > 0) // <2>
            .order(-1);
        routes.GET("/items/{name}", (request, pathVariables) -> text("item named " + pathVariables.getString("name"))); // <3>
        // end::constrain[]

        // tag::attributes[]
        routes.path("/reports", reports -> {
            reports.attribute("role", "auditor"); // <1>
            reports.beforeReplacing(request -> {
                String role = RouteAttributes.getRouteInfo(request) // <2>
                    .flatMap(route -> route.getAttribute("role", String.class))
                    .orElseThrow();
                return role.equals(request.getHeaders().get("X-Role")) ? null : HttpResponse.status(HttpStatus.FORBIDDEN);
            });
            reports.GET("/daily", (request, pathVariables) -> text("daily report"));
            reports.GET("/salaries", (request, pathVariables) -> text("salaries"))
                .attribute("role", "admin"); // <3>
        });
        // end::attributes[]

        // tag::port[]
        routes.path("/management", management -> {
            management.port(managementPort); // <1>
            management.GET("/health", (request, pathVariables) -> text("UP"));
        });
        // end::port[]
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }
}
