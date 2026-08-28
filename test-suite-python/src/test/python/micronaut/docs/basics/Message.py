from dataclasses import dataclass

# tag::imports[]
from micronaut.core.annotation import Introspected, ReflectiveAccess
# end::imports[]


# tag::class[]
@ReflectiveAccess
@Introspected
@dataclass
class Message:
    text: str | None = None
# end::class[]
