from micronaut.http import HttpResponse
from micronaut.http.annotation import Controller, Get

from .ShoppingCart import ShoppingCart


@Controller("/customBinding")
class ShoppingCartController:

    # tag::method[]
    @Get("/typed")
    def loadCart(self, shoppingCart: ShoppingCart) -> HttpResponse:  # <1>
        responseMap = {}
        responseMap["sessionId"] = shoppingCart.sessionId
        responseMap["total"] = shoppingCart.total

        return HttpResponse.ok(responseMap)
    # end::method[]
