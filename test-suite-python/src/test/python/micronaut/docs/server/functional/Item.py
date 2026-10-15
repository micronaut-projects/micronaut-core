from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Item:
    """An item of the documentation examples."""

    id: int
    name: str
