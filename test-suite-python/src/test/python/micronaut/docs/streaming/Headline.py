from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Headline:
    text: str | None = None
