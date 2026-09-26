package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
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

    private final ShopRoutes shopRoutes = new ShopRoutes(); // <1>

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.locate("/shops/{shop}", (request, pathVariables) -> // <2>
            SHOPS.get(pathVariables.getString("shop")), shopRoutes);

        routes.locateAsync("/remote-shops/{shop}", (request, pathVariables) -> // <3>
            CompletableFuture.supplyAsync(() -> SHOPS.get(pathVariables.getString("shop"))), shopRoutes);

        routes.locate("/archived-shops/{shop}", (request, pathVariables) -> // <4>
            SHOPS.get(pathVariables.getString("shop")), shop -> shop.items().size() > 1 ? shopRoutes : SmallShopRoutes.INSTANCE);
    }

    /**
     * The routes of a located shop.
     */
    static final class ShopRoutes implements LocatedRoutes<Shop> {

        @Override
        public Argument<Shop> targetType() { // <5>
            return Argument.of(Shop.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Shop> shop) { // <6>
            shop.handle(HttpMethod.GET, (request, pathVariables, located) -> HttpResponse.ok(located.name()));
            shop.handle(HttpMethod.GET, "/items/{index}", (request, pathVariables, located) ->
                HttpResponse.ok(located.items().get(pathVariables.getInt("index"))));
        }
    }

    /**
     * The routes of a shop with a single item.
     */
    enum SmallShopRoutes implements LocatedRoutes<Shop> {
        INSTANCE;

        @Override
        public Argument<Shop> targetType() {
            return Argument.of(Shop.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Shop> shop) {
            shop.GET("/item", (request, pathVariables) ->
                HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables, Shop.class).items().get(0))); // <7>
        }
    }
}
// end::clazz[]
