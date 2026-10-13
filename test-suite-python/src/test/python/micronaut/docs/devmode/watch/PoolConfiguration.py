from typing import Annotated, Optional

from micronaut.context.annotation import EachProperty, Parameter, Requires


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@EachProperty("pools")
class PoolConfiguration:
    """The configuration of one pool, under pools.<name>."""
    url: Optional[str] = None
    username: Optional[str] = None
    password: Optional[str] = None

    def __init__(self, name: Annotated[str, Parameter]):
        self.name = name
