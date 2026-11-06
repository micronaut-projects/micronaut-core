from typing import Annotated

from jakarta.persistence import EmbeddedId, Entity, Table

from micronaut.docs.hibernate.OrderId import OrderId


# tag::clazz[]
@Entity
@Table(name="orders")
class Order:
    id: Annotated[OrderId | None, EmbeddedId] = None
    customer: str | None = None
# end::clazz[]
