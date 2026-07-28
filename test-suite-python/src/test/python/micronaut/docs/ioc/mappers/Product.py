# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class Product:
    name: str
    price: float
    manufacturer: str
# end::class[]
