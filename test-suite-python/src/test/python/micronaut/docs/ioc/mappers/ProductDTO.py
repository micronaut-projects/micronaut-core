# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class ProductDTO:
    name: str
    price: str
    distributor: str
# end::class[]
