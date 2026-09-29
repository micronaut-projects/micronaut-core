from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
class Note:
    """A Python type with identity semantics: no ``__eq__``, so it is equal only to itself."""

    text: str | None = None

    def __init__(self, text: str | None = None) -> None:
        self.text = text
