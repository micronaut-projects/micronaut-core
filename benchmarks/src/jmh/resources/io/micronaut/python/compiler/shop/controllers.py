from typing import Annotated

from jakarta.validation.constraints import Min, NotBlank
from micronaut.http.annotation import Controller, Get, Post

from services import CatalogService, OrderService


@Controller("/catalog")
class CatalogController:
    def __init__(self, catalog: CatalogService):
        self.catalog = catalog

    @Post("/{sku}/{name}/{price_cents}/{stock}")
    def add(self, sku: Annotated[str, NotBlank], name: Annotated[str, NotBlank], price_cents: Annotated[int, Min(0)], stock: Annotated[int, Min(0)]) -> str:
        product = self.catalog.add(sku, name, price_cents, stock)
        return product.sku

    @Get("/{sku}/price")
    def price(self, sku: str) -> int:
        return self.catalog.price_of(sku)

    @Post("/{sku}/restock/{quantity}")
    def restock(self, sku: str, quantity: Annotated[int, Min(1)]) -> int:
        return self.catalog.restock(sku, quantity)

    @Get("/size")
    def size(self) -> int:
        return self.catalog.catalog_size()

    @Get("/describe")
    def describe(self) -> str:
        return self.catalog.describe()


@Controller("/orders")
class OrderController:
    def __init__(self, orders: OrderService):
        self.orders = orders

    @Post("/{customer}/{sku}/{quantity}")
    def place(self, customer: Annotated[str, NotBlank], sku: Annotated[str, NotBlank], quantity: Annotated[int, Min(1)]) -> str:
        order = self.orders.place(customer, sku, quantity)
        return order.summary()

    @Get("/revenue")
    def revenue(self) -> int:
        return self.orders.revenue()

    @Get("/{customer}/count")
    def count(self, customer: str) -> int:
        return self.orders.orders_of(customer)

    @Get("/average")
    def average(self) -> float:
        return self.orders.average_cents()
