# tag::imports[]
from jakarta.inject import Qualifier
from micronaut.context.annotation import NonBinding
from typing import Annotated
# end::imports[]

# tag::class[]
@Qualifier
def Cylinders(value: int,
              description: Annotated[str, NonBinding] = ""):
    def decorator(func):
        return func
    return decorator
# end::class[]
