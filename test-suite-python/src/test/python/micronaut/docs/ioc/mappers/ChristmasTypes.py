from dataclasses import dataclass

from micronaut.core.annotation import Introspected


# tag::beans[]
@dataclass
@Introspected
class ChristmasPresent:
    packaging_color: str | None
    type: str
    weight: float
    greeting_card: str | None


@dataclass
@Introspected
class PresentPackaging:
    weight: float
    color: str


@dataclass
@Introspected
class Present:
    weight: float
    type: str
# end::beans[]
