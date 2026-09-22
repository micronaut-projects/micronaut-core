package catalog.web;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteBuilder;
import jakarta.inject.Singleton;

/**
 * Binds handler functions to the declared routes of the catalog resources.
 */
@Singleton
@Requires(property = "spec.name", value = CompiledRouteSelectionTest.SPEC_NAME)
public class CatalogRoutes implements HttpRoutes {
    @Override
    public void routes(RouteBuilder routes) {
        routes.handle(ItemResourceRoutes.ITEM, (request, path) ->
            HttpResponse.ok("declared item " + path.getString("id")).header("X-Route", "declared"))
            .produces(MediaType.APPLICATION_JSON_TYPE);
        routes.handle(ItemSpecialResourceRoutes.SPECIAL, (request, path) ->
            HttpResponse.ok("declared special").header("X-Route", "special"))
            .produces(MediaType.APPLICATION_JSON_TYPE);
        routes.handle(DocResourceRoutes.DOC, (request, path) ->
            HttpResponse.ok("declared doc " + path.getString("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
    }
}
