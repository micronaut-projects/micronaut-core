from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Note:
    text: str | None = None
    priority: int = 0
