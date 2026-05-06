from micronaut.context import ApplicationContext
from org.junit.jupiter.api import Disabled, Test

from .AdditionalMappers import AdditionalMappers, Card
from .ChristmasMappers import ChristmasMappers
from .ChristmasTypes import Present, PresentPackaging
from .Product import Product
from .ProductMappers import ProductMappers


@Disabled("Python @Mapper interface introduction is not fully validated yet")
class MappersSpec:
    @Test
    def test_mappers(self) -> None:
        context = ApplicationContext.run()
        try:
            # tag::mappers[]
            product_mappers = context.getBean(ProductMappers)

            product_dto = product_mappers.to_product_dto(Product(
                "MacBook",
                910.50,
                "Apple",
            ))

            assert product_dto.name == "MacBook"
            assert product_dto.price == "$1821.00"
            assert product_dto.distributor == "Great Product Company"
            # end::mappers[]
        finally:
            context.close()

    @Test
    def test_merging(self) -> None:
        context = ApplicationContext.run()
        try:
            # tag::merge[]
            mappers = context.getBean(ChristmasMappers)

            result = mappers.merge(
                PresentPackaging(1.0, "red"),
                Present(10.0, "teddy bear"),
            )

            assert result.weight == 11.0
            assert result.packaging_color == "red"
            assert result.type == "teddy bear"
            assert result.greeting_card == "Merry christmas"
            # end::merge[]
        finally:
            context.close()

    @Test
    def test_additional_mappers(self) -> None:
        context = ApplicationContext.run()
        try:
            # tag::additional[]
            mappers = context.getBean(AdditionalMappers)

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
                    "packagingColor": "blue",
                    "christmasCard": "Merry Christmas!",
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
        finally:
            context.close()
