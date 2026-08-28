from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@dataclass
@ReflectiveAccess
@Introspected
class Point:
    x: int
    y: int
