from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from jakarta.inject import Inject
from typing import Annotated

from .AdditionalMappers import AdditionalMappers, Card
from .ChristmasMappers import ChristmasMappers
from .ChristmasTypes import Present, PresentPackaging
from .Product import Product
from .ProductMappers import ProductMappers


@MicronautTest
class MappersSpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_mappers(self) -> None:
        # tag::mappers[]
        product_mappers = self.context.getBean(ProductMappers)

        product_dto = product_mappers.to_product_dto(Product(
            "MacBook",
            910.50,
            "Apple",
        ))

        assert product_dto.name == "MacBook", (product_dto.name, product_dto.price, product_dto.distributor)
        assert product_dto.price == "$1821.00", (product_dto.name, product_dto.price, product_dto.distributor)
        assert product_dto.distributor == "Great Product Company", (product_dto.name, product_dto.price, product_dto.distributor)
        # end::mappers[]

    @Test
    def test_merging(self) -> None:
        # tag::merge[]
        mappers = self.context.getBean(ChristmasMappers)

        result = mappers.merge(
            PresentPackaging(1.0, "red"),
            Present(10.0, "teddy bear"),
        )

        assert result.weight == 11.0
        assert result.packaging_color == "red"
        assert result.type == "teddy bear"
        assert result.greeting_card == "Merry christmas"
        # end::merge[]

    @Test
    def test_additional_mappers(self) -> None:
        # tag::additional[]
        mappers = self.context.getBean(AdditionalMappers)

        result = mappers.merge(
            PresentPackaging(1.0, "red"),
            Present(10.0, "teddy bear"),
            Card("Merry Christmas!"),
        )

        assert result.weight == 10.0
        assert result.packaging_color is None
        assert result.type == "teddy bear"
        assert result.greeting_card == "Merry Christmas!"

        result = mappers.update(
            result,
            {
                "packaging_color": "blue",
                "christmas_card": "Merry Christmas!",
            },
        )

        assert result.weight == 10.0
        assert result.packaging_color == "blue"
        assert result.type == "teddy bear"
        assert result.greeting_card == "Merry Christmas!!!"

        result = mappers.merge_with_merge_strategy(
            PresentPackaging(1.0, "red"),
            Present(10.0, "teddy bear"),
        )

        assert result.weight == 11.0
        assert result.packaging_color == "red"
        assert result.type == "teddy bear"
        # end::additional[]
