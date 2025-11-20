from jakarta.inject import Singleton, Named
from .Engine import Engine
from .V8 import V8
from typing import Annotated

# tag::class[]
@Singleton
class Vehicle:
    # tag::constructor[]
    def __init__(self, engine: Annotated[Engine, V8]):
        self.engine = engine
    # end::constructor[]

    def start(self) -> str:
        return self.engine.start() # <5>
# end::class[]
