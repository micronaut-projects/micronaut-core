from typing import Annotated

from jakarta.inject import Inject
from jakarta.validation.constraints import NotBlank
from micronaut.http.annotation import Get, Post

from services import CatalogService, OrderService


catalog: Annotated[CatalogService, Inject]
orders: Annotated[OrderService, Inject]


@Get("/routes/size")
def route_size() -> int:
    return catalog.catalog_size()


@Get("/routes/revenue")
def route_revenue() -> int:
    return orders.revenue()


@Post("/routes/restock/{sku}/{quantity}")
def route_restock(sku: Annotated[str, NotBlank], quantity: int) -> int:
    return catalog.restock(sku, quantity)


@Get("/routes/describe")
def route_describe() -> str:
    return catalog.describe()
