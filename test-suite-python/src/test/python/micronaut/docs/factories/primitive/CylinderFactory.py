# tag::imports[]
from micronaut.context.annotation import Bean, Factory
from jakarta.inject import Named
from typing import Annotated
# end::imports[]

# tag::class[]
@Factory
class CylinderFactory:
    v8 : Annotated[int, Bean, Named("V8")] = 8 # (1)
    v6 : Annotated[int, Bean, Named("V6")] = 6 # (1)
# end::class[]
