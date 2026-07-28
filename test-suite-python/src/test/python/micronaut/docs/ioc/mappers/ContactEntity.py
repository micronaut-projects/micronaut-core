# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class ContactEntity:
    id: int | None
    first_name: str
    last_name: str
# end::class[]
