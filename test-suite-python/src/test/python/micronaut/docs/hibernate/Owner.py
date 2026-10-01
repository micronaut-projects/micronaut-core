from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id
from java.lang import Long
from micronaut.configuration.hibernate.jpa.proxy import GenerateProxy


# tag::clazz[]
@Entity
@GenerateProxy
class Owner:
    id: Annotated[Long | None, Id, GeneratedValue] = None
    name: str | None = None
# end::clazz[]
