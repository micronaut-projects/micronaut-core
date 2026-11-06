from dataclasses import dataclass
from decimal import Decimal
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, MappedProperty
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository


@MappedEntity("decimal_prices")
@dataclass
class DecimalPrice:
    id: Annotated[int | None, Id, GeneratedValue]
    code: str
    amount: Annotated[Decimal, MappedProperty(definition="DECIMAL(38,20)")]


@JdbcRepository(dialect="H2")
class DecimalPriceRepository(CrudRepository[DecimalPrice, int]):
    def findByCode(self, code: str) -> DecimalPrice | None: ...

    def updateByCode(self, code: str, amount: Decimal) -> int: ...
