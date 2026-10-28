from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Min, NotBlank
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Product:
    sku: Annotated[str, NotBlank] = ""
    name: Annotated[str, NotBlank] = ""
    price_cents: Annotated[int, Min(0)] = 0
    stock: Annotated[int, Min(0)] = 0

    def total_cents(self, quantity: int) -> int:
        return self.price_cents * quantity

    def available(self, quantity: int) -> bool:
        return quantity > 0 and quantity <= self.stock


@Introspected
@dataclass
class OrderLine:
    sku: Annotated[str, NotBlank] = ""
    quantity: Annotated[int, Min(1)] = 1


@Introspected
@dataclass
class Order:
    id: int = 0
    customer: Annotated[str, NotBlank] = ""
    total_cents: int = 0
    lines: int = 0

    def summary(self) -> str:
        return self.customer + ": " + str(self.lines) + " lines, " + str(self.total_cents) + " cents"
