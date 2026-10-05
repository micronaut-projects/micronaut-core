from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation import Valid
from jakarta.validation.constraints import NotBlank
from micronaut.context.annotation import Requires
from micronaut.core.annotation import Introspected
from micronaut.http import HttpResponse
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes


@Introspected
@dataclass
class Product:
    name: Annotated[str, NotBlank]


@Requires(property="spec.name", value="ValidatedRoutesTest")
# tag::service[]
@Singleton
class ProductService:
    def save(self, product: Annotated[Product, Valid]) -> Product:  # <1>
        return product
# end::service[]


@Requires(property="spec.name", value="ValidatedRoutesTest")
@Singleton
class ValidatedRoutes(HttpRoutes):

    def __init__(self, products: ProductService):
        self.products = products

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::route[]
        routes.POST("/products").body(Product).handle(lambda request, path_variables, product:
                    HttpResponse.created(self.products.save(product)))  # <1>
        # end::route[]
