from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import NotBlank
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Person:
    name: Annotated[str, NotBlank]
