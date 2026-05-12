from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import Value


@Singleton
class ContextConsumer:
    random_field: Annotated[int, Value("#{ generateRandom(1, 10) }")]
