from dataclasses import dataclass

from jakarta.persistence import Embeddable
from java.io import Serializable
from java.lang import Long


# tag::clazz[]
@Embeddable
@dataclass(eq=True, frozen=True)
class OrderId(Serializable):
    region: str | None = None
    number: Long | None = None
# end::clazz[]
