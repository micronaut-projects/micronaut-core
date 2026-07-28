# tag::imports[]
from jakarta.inject import Singleton
# end::imports[]

from .Product import Product


# tag::class[]
@Singleton
class ProductService:
    def __init__(self) -> None:
        self.products: dict[str, Product] = {}

    def add_product(self, product: Product) -> None:
        self.products[product.get_product_name()] = product

    def remove_product(self, product: Product) -> None:
        product.set_active(False)
        self.products.pop(product.get_product_name(), None)

    def find_product(self, name: str) -> Product | None:
        return self.products.get(name)
# end::class[]
