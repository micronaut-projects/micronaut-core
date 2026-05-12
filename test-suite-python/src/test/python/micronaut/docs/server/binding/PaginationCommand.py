from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Pattern, Positive, PositiveOrZero
from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class PaginationCommand:
    offset: Annotated[int | None, PositiveOrZero] = None
    max: Annotated[int | None, Positive] = None
    sort: Annotated[str | None, Pattern(regexp="name|href|title")] = None
    order: Annotated[str | None, Pattern(regexp="asc|desc|ASC|DESC")] = None
