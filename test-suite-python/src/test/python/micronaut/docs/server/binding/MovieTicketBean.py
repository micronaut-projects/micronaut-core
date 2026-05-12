from dataclasses import dataclass
from typing import Annotated

# tag::imports[]
import java
from jakarta.validation.constraints import PositiveOrZero
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import PathVariable, QueryValue

HttpRequest = java.type("io.micronaut.http.HttpRequest")
# end::imports[]


# tag::class[]
@dataclass
@Introspected
class MovieTicketBean:
    httpRequest: HttpRequest
    movieId: Annotated[str, PathVariable]
    minPrice: Annotated[float | None, QueryValue, PositiveOrZero] = None
    maxPrice: Annotated[float | None, QueryValue, PositiveOrZero] = None
# end::class[]
