package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.net.URI;
// end::imports[]

@Requires(property = "spec.name", value = "GroupRoutesTest")
@Singleton
public class GroupRoutes implements HttpRoutes {

    /**
     * The tenant of a request, propagated to the filters and the handlers.
     *
     * @param id The tenant id
     */
    public record Tenant(String id) implements PropagatedContextElement {
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::groups[]
        routes.path("/api", api -> { // <1>
            api.before((request, propagatedContext) -> { // <2>
                String tenant = request.getHeaders().get("X-Tenant");
                if (tenant == null) {
                    return HttpResponse.badRequest("Missing tenant");
                }
                propagatedContext.add(new Tenant(tenant));
                return null;
            });
            api.after((request, response) -> response.header("X-Api", "v1")); // <3>

            api.GET("/orders", (request, pathVariables) ->
                text("orders of " + PropagatedContext.get().get(Tenant.class).id()));

            api.path("/admin", admin -> { // <4>
                admin.before(request -> "admin".equals(request.getHeaders().get("X-Role"))
                    ? null
                    : HttpResponse.status(HttpStatus.FORBIDDEN));
                admin.GET("/users", (request, pathVariables) -> text("users"));
            });

            api.GET("/reports/{id}", (request, pathVariables) ->
                    text("report " + pathVariables.getInt("id") + " as " + request.getHeaders().get("X-Report-Format")))
                .before(request -> { // <5>
                    request.getHeaders().add("X-Report-Format", "summary");
                    return null;
                })
                .afterReplacing((request, response) -> // <6>
                    response.getStatus() == HttpStatus.OK && request.getHeaders().contains("X-Legacy")
                        ? HttpResponse.status(HttpStatus.GONE)
                        : null);

            api.GET((request, pathVariables) -> text("api of " + PropagatedContext.get().get(Tenant.class).id())); // <7>
        });
        // end::groups[]

        // tag::serverFilters[]
        routes.filter("/api/**").order(100) // <1>
            .after((request, response) -> response.header("X-Served-By", "api"));

        routes.filter("/v1/**").preMatching() // <2>
            .before(request -> {
                request.uri(URI.create(request.getUri().toString().replaceFirst("^/v1", "/api")));
                return null;
            });
        // end::serverFilters[]
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }
}
