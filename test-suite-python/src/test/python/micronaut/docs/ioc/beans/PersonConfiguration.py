# tag::class[]
from micronaut.core.annotation import Introspected

from .Person import Person


@Introspected(classes=Person)
class PersonConfiguration:
    pass
# end::class[]
