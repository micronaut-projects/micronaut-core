from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import Property, Requires


@Requires(property="provided.greeting")
@Singleton
class ProvidedGreeter:
    """A bean that exists only when the property the test provides is set."""

    greeting: Annotated[str, Property(name="provided.greeting")]

    def greet(self, name: str) -> str:
        return f"{self.greeting} {name}"
