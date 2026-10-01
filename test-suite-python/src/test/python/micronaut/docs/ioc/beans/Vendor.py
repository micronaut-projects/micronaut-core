from dataclasses import dataclass

from micronaut.core.annotation import Creator, Introspected


@dataclass(frozen=True)
@Introspected(constructors=True)
class Vendor:
    name: str

    @classmethod
    @Creator
    def for_name(cls, name: str) -> "Vendor":
        return cls(name)
