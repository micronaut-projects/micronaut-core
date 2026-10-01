# tag::imports[]
from dataclasses import dataclass
from micronaut.core.annotation import Introspected
# end::imports[]

# tag::class[]
@dataclass
@Introspected
class Person:
    name : str
    age : int = 18
# end::class[]
