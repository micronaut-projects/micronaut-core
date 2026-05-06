from typing import Annotated

# tag::imports[]
from jakarta.annotation import PreDestroy
from micronaut.context.annotation import Parameter
# end::imports[]

from .ProductBean import ProductBean


# tag::class[]
@ProductBean  # <1>
class Product:
    active: bool = False

    def __init__(self, product_name: Annotated[str, Parameter]):  # <2>
        self.product_name = product_name

    def get_product_name(self) -> str:
        return self.product_name

    def is_active(self) -> bool:
        return self.active

    def set_active(self, active: bool) -> None:
        self.active = active

    @PreDestroy  # <3>
    def disable(self) -> None:
        self.active = False
# end::class[]
