# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import Creator, Introspected


@dataclass(frozen=True)
@Introspected
class Business:
    name: str

    @classmethod
    @Creator  # <1>
    def for_name(cls, name: str) -> "Business":
        return cls(name)
# end::class[]
