from typing import Annotated

from micronaut.http import HttpResponse
from micronaut.http.annotation import Controller, Get

from .ShoppingCart import ShoppingCart


@Controller("/customBinding")
class ShoppingCartController:

    # tag::method[]
    @Get("/annotated")
    def checkSession(self, sessionId: Annotated[int, ShoppingCart]) -> HttpResponse:  # <1>
        return HttpResponse.ok("Session:" + str(sessionId))
    # end::method[]
