from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class OtherError:
    status: int
    error: str
    message: str
    path: str
