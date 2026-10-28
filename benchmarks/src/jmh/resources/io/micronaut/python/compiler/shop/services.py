from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import Min, NotBlank

from models import Order, Product
from repository import OrderRepository, ProductRepository


@Singleton
class CatalogService:
    def __init__(self, products: ProductRepository):
        self.products = products

    def add(self, sku: Annotated[str, NotBlank], name: Annotated[str, NotBlank], price_cents: Annotated[int, Min(0)], stock: Annotated[int, Min(0)]) -> Product:
        product = Product(sku, name, price_cents, stock)
        return self.products.save(product)

    def price_of(self, sku: str) -> int:
        if not self.products.exists(sku):
            return -1
        return self.products.find(sku).price_cents

    def restock(self, sku: str, quantity: Annotated[int, Min(1)]) -> int:
        product = self.products.find(sku)
        product.stock = product.stock + quantity
        return product.stock

    def catalog_size(self) -> int:
        return self.products.count()

    def describe(self) -> str:
        return ", ".join(self.products.skus())


@Singleton
class OrderService:
    def __init__(self, products: ProductRepository, orders: OrderRepository):
        self.products = products
        self.orders = orders

    def place(self, customer: Annotated[str, NotBlank], sku: Annotated[str, NotBlank], quantity: Annotated[int, Min(1)]) -> Order:
        product = self.products.find(sku)
        if not product.available(quantity):
            raise ValueError("out of stock: " + sku)
        product.stock = product.stock - quantity
        order = Order(0, customer, product.total_cents(quantity), 1)
        return self.orders.save(order)

    def revenue(self) -> int:
        return self.orders.total_cents()

    def orders_of(self, customer: str) -> int:
        return self.orders.by_customer(customer)

    def average_cents(self) -> float:
        count = self.orders.count()
        if count == 0:
            return 0.0
        return self.orders.total_cents() / count
