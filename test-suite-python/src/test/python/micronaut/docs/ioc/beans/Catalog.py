# tag::class[]
from dataclasses import dataclass
from typing import Generic, TypeVar
from micronaut.core.annotation import Introspected

T = TypeVar("T")

@dataclass
@Introspected
class Catalog(Generic[T]):
    prices : list[float]
    items : list[T]
# end::class[]
