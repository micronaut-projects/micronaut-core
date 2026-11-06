from jakarta.inject import Singleton

from models import Order, Product


@Singleton
class ProductRepository:
    def __init__(self):
        self.products: dict[str, Product] = {}

    def save(self, product: Product) -> Product:
        self.products[product.sku] = product
        return product

    def find(self, sku: str) -> Product:
        return self.products[sku]

    def exists(self, sku: str) -> bool:
        return sku in self.products

    def count(self) -> int:
        return len(self.products)

    def skus(self) -> list[str]:
        result: list[str] = []
        for sku in self.products:
            result.append(sku)
        return result

    def in_stock(self) -> int:
        count = 0
        for sku, product in self.products.items():
            if product.stock > 0:
                count = count + 1
        return count


@Singleton
class OrderRepository:
    def __init__(self):
        self.orders: list[Order] = []
        self.next_id: int = 1

    def save(self, order: Order) -> Order:
        order.id = self.next_id
        self.next_id = self.next_id + 1
        self.orders.append(order)
        return order

    def count(self) -> int:
        return len(self.orders)

    def total_cents(self) -> int:
        total = 0
        for order in self.orders:
            total = total + order.total_cents
        return total

    def by_customer(self, customer: str) -> int:
        count = 0
        for order in self.orders:
            if order.customer == customer:
                count = count + 1
        return count
