from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Tick:
    index: int = 0
    label: str = ""
