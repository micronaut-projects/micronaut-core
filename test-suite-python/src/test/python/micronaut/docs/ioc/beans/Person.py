# tag::class[]
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@dataclass
@Introspected
class Person:
    name : str
    age : int
# end::class[]
