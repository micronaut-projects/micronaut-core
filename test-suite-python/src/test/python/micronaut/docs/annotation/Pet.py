from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


# tag::class[]
@ReflectiveAccess
@Introspected
@dataclass
class Pet:
    name: str | None = None
    age: int = 0
# end::class[]
