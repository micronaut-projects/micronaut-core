# tag::imports[]
from typing import Annotated

from micronaut.context.annotation import AliasFor
from .Limit import Limit
# end::imports[]

# tag::class[]
@Limit(max=50)
def MaxLength(value: Annotated[int, AliasFor(annotation=Limit, member="max", applyDefault=True)] = 50):
    def decorator(func):
        return func

    return decorator
# end::class[]
