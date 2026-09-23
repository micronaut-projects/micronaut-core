package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
// end::imports[]

@Requires(property = "spec.name", value = "LocatorRoutesTest")
// tag::clazz[]
@Singleton
public class LocatorRoutes implements HttpRoutes {

    /**
     * A located target.
     *
     * @param name  The name of the shop
     * @param items The items of the shop
     */
    public record Shop(String name, List<String> items) {
    }

    private static final Map<String, Shop> SHOPS = Map.of(
        "north", new Shop("north", List.of("tea", "coffee")),
        "south", new Shop("south", List.of("juice")));

    private final RouteTable shopRoutes;

    LocatorRoutes(RouteTableFactory tables) {
        shopRoutes = tables.buildLocatedHttpRoutes(shop -> { // <1>
            shop.GET("/", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.locatedTarget(Shop.class).name())); // <2>
            shop.GET("/items/{index}", (request, pathVariables) ->
                HttpResponse.ok(pathVariables.locatedTarget(Shop.class).items().get(pathVariables.getInt("index"))));
        });
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.locate("/shops/{shop}", (request, pathVariables) -> // <3>
            SHOPS.get(pathVariables.getString("shop")), shop -> shopRoutes);

        routes.locateAsync("/remote-shops/{shop}", (request, pathVariables) -> // <4>
            CompletableFuture.supplyAsync(() -> SHOPS.get(pathVariables.getString("shop"))), shop -> shopRoutes);
    }
}
// end::clazz[]
