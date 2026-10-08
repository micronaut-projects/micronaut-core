package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder
import io.micronaut.web.router.builder.LocatedRoutes
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "LocatorRoutesSpec")
// tag::clazz[]
@Singleton
class LocatorRoutes implements HttpRoutes {

    /**
     * A located target.
     *
     * @param name  The name of the shop
     * @param items The items of the shop
     */
    static record Shop(String name, List<String> items) {
    }

    private static final Map<String, Shop> SHOPS = [
        north: new Shop("north", ["tea", "coffee"]),
        south: new Shop("south", ["juice"])]

    private final ShopRoutes shopRoutes = new ShopRoutes() // <1>

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.locate("/shops/{shop}", { request, pathVariables -> // <2>
            SHOPS.get(pathVariables.getString("shop"))
        }, shopRoutes)
        routes.locateAsync("/remote-shops/{shop}", { request, pathVariables -> // <3>
            CompletableFuture.supplyAsync { SHOPS.get(pathVariables.getString("shop")) }
        }, shopRoutes)
        routes.locate("/archived-shops/{shop}", { request, pathVariables -> // <4>
            SHOPS.get(pathVariables.getString("shop"))
        }, { Shop shop -> shop.items().size() > 1 ? shopRoutes : SmallShopRoutes.INSTANCE })
    }

    /**
     * The routes of a located shop.
     */
    static final class ShopRoutes implements LocatedRoutes<Shop> {
        @Override
        Argument<Shop> targetType() { // <5>
            return Argument.of(Shop)
        }

        @Override
        void routes(LocatedHttpRouteBuilder<Shop> shop) { // <6>
            shop.GET("/").handle { request, pathVariables, Shop located -> HttpResponse.ok(located.name()) }
            shop.GET("/items/{index}").handle { request, pathVariables, Shop located ->
                HttpResponse.ok(located.items().get(pathVariables.getInt("index")))
            }
        }
    }

    /**
     * The routes of a shop with a single item.
     */
    static enum SmallShopRoutes implements LocatedRoutes<Shop> {
        INSTANCE

        @Override
        Argument<Shop> targetType() {
            return Argument.of(Shop)
        }

        @Override
        void routes(LocatedHttpRouteBuilder<Shop> shop) {
            shop.GET("/item") { request, pathVariables ->
                HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables, Shop).items().get(0)) // <7>
            }
        }
    }
}
// end::clazz[]
