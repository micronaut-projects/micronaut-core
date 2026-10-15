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

@Requires(property = "spec.name", value = "LocatorRoutesTest")
// tag::clazz[]
@Singleton
class LocatorRoutes : HttpRoutes {

    /**
     * A located target.
     *
     * @param name  The name of the shop
     * @param items The items of the shop
     */
    data class Shop(val name: String, val items: List<String>)

    private val shopRoutes = ShopRoutes() // <1>

    override fun routes(routes: HttpRouteBuilder) {
        routes.locate("/shops/{shop}", { request, pathVariables -> // <2>
            SHOPS[pathVariables.getString("shop")]
        }, shopRoutes)
        routes.locateAsync("/remote-shops/{shop}", { request, pathVariables -> // <3>
            CompletableFuture.supplyAsync { SHOPS[pathVariables.getString("shop")] }
        }, shopRoutes)
        routes.locate("/archived-shops/{shop}", { request, pathVariables -> // <4>
            SHOPS[pathVariables.getString("shop")]
        }, { shop: Shop -> if (shop.items.size > 1) shopRoutes else SmallShopRoutes.INSTANCE })
    }

    /**
     * The routes of a located shop.
     */
    class ShopRoutes : LocatedRoutes<Shop> {
        override fun targetType(): Argument<Shop> = Argument.of(Shop::class.java) // <5>

        override fun routes(shop: LocatedHttpRouteBuilder<Shop>) { // <6>
            shop.GET("/").handle { request, pathVariables, located -> HttpResponse.ok(located.name) }
            shop.GET("/items/{index}").handle { request, pathVariables, located ->
                HttpResponse.ok(located.items[pathVariables.getInt("index")])
            }
        }
    }

    /**
     * The routes of a shop with a single item.
     */
    enum class SmallShopRoutes : LocatedRoutes<Shop> {
        INSTANCE;

        override fun targetType(): Argument<Shop> = Argument.of(Shop::class.java)

        override fun routes(shop: LocatedHttpRouteBuilder<Shop>) {
            shop.GET("/item") { request, pathVariables ->
                HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables, Shop::class.java).items[0]) // <7>
            }
        }
    }

    companion object {
        private val SHOPS = mapOf(
            "north" to Shop("north", listOf("tea", "coffee")),
            "south" to Shop("south", listOf("juice")))
    }
}
// end::clazz[]
