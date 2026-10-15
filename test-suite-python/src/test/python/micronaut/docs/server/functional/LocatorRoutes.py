from micronaut.context.annotation import Requires
# tag::imports[]
from dataclasses import dataclass

import java
from jakarta.inject import Singleton
from micronaut.core.type import Argument
from micronaut.http import HttpResponse
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes, LocatedHttpRouteBuilder, LocatedRoutes

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
# end::imports[]


# tag::clazz[]
@dataclass
class Shop:
    """A located target."""

    name: str
    items: list[str]


SHOPS = {
    "north": Shop("north", ["tea", "coffee"]),
    "south": Shop("south", ["juice"]),
}


# end::clazz[]
@Requires(property="spec.name", value="LocatorRoutesTest")
# tag::clazz[]
@Singleton
class LocatorRoutes(HttpRoutes):

    def __init__(self):
        self.shop_routes = ShopRoutes()  # <1>

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.locate("/shops/{shop}", lambda request, path_variables:  # <2>
                      SHOPS.get(path_variables.getString("shop")), self.shop_routes)
        routes.locateAsync("/remote-shops/{shop}", lambda request, path_variables:  # <3>
                           CompletableFuture.supplyAsync(lambda: SHOPS.get(path_variables.getString("shop"))), self.shop_routes)
        routes.locate("/archived-shops/{shop}", lambda request, path_variables:  # <4>
                      SHOPS.get(path_variables.getString("shop")),
                      lambda shop: self.shop_routes if len(shop.items) > 1 else SMALL_SHOP_ROUTES)


class ShopRoutes(LocatedRoutes[Shop]):
    """The routes of a located shop."""

    def targetType(self) -> Argument:  # <5>
        return Argument.of(Shop)

    def routes(self, shop: LocatedHttpRouteBuilder) -> None:  # <6>
        shop.GET("/").handle(lambda request, path_variables, located: HttpResponse.ok(located.name))
        shop.GET("/items/{index}").handle(lambda request, path_variables, located:
                    HttpResponse.ok(located.items[path_variables.getInt("index")]))


class SmallShopRoutes(LocatedRoutes[Shop]):
    """The routes of a shop with a single item."""

    def targetType(self) -> Argument:
        return Argument.of(Shop)

    def routes(self, shop: LocatedHttpRouteBuilder) -> None:
        shop.GET("/item", lambda request, path_variables:
                 HttpResponse.ok(LocatedRoutes.locatedTarget(path_variables, Shop).items[0]))  # <7>


SMALL_SHOP_ROUTES = SmallShopRoutes()
# end::clazz[]
