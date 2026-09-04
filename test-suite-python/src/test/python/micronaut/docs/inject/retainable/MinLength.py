# tag::imports[]
from typing import Annotated

from micronaut.context.annotation import AliasFor
from micronaut.core.annotation import Retainable
from .Limit import Limit
# end::imports[]

# tag::class[]
@Retainable
@Limit(min=5)  # <1>
def MinLength(value: Annotated[int, AliasFor(annotation=Limit, member="min", applyDefault=True)] = 5):  # <2>
    def decorator(func):
        return func

    return decorator
# end::class[]
