from typing import Annotated

from micronaut.context.annotation import AliasFor
from micronaut.core.bind.annotation import Bindable


# tag::clazz[]
@Bindable
def NameAuthorization(
    value: Annotated[str, AliasFor(member="name")] = "",
    name: Annotated[str, AliasFor(member="value")] = "",
):
    def decorator(func):
        return func
    return decorator
# end::clazz[]
