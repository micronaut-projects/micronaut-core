from dataclasses import dataclass

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Person:
    name: str = ""
    age: int = 0
