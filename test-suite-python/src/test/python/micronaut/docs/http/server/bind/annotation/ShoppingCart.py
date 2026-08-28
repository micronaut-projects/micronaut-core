from typing import Annotated

from micronaut.context.annotation import AliasFor
from micronaut.core.bind.annotation import Bindable


# tag::class[]
@Bindable  # <1>
def ShoppingCart(
    value: Annotated[str, AliasFor(annotation=Bindable, member="value")] = "",
):
    def decorator(func):
        return func
    return decorator
# end::class[]
