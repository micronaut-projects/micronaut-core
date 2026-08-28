# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import AccessorsStyle, Introspected


@dataclass
@Introspected
@AccessorsStyle(readPrefixes=[""], writePrefixes=[""])  # <1>
class Person:
    name: str  # <2>
    age: int  # <2>
# end::class[]
