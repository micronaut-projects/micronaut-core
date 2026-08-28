from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class CustomError:
    status: int | None = None
    error: str | None = None
    message: str | None = None
    path: str | None = None
