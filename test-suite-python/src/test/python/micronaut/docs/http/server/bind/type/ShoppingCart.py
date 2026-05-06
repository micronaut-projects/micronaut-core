from dataclasses import dataclass

from micronaut.core.annotation import Introspected


# tag::class[]
@dataclass
@Introspected
class ShoppingCart:
    sessionId: str | None = None
    total: int | None = None
# end::class[]
