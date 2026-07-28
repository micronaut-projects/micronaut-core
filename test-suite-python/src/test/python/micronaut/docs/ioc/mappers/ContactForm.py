# tag::class[]
from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class ContactForm:
    first_name: str
    last_name: str
# end::class[]
