from abc import ABC, abstractmethod

from .Product import Product
from .ProductDTO import ProductDTO


# tag::class[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Mapper


@Singleton
class ProductMappers(ABC):
    @Mapper.Mapping(
        to="price",
        **{"from": "#{product.price * 2}", "format": "$#.00"},
    )
    @Mapper.Mapping(
        to="distributor",
        **{"from": "#{this.get_distributor()}"},
    )
    @abstractmethod
    def to_product_dto(self, product: Product) -> ProductDTO:
        ...

    def get_distributor(self) -> str:
        return "Great Product Company"
# tag::end[]
