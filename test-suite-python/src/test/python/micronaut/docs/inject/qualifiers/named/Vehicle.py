from jakarta.inject import Singleton, Named
from .Engine import Engine
from typing import Annotated

# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine: Annotated[Engine, Named("v8")]): # <4>
        self.engine = engine

    def start(self) -> str:
        return self.engine.start() # <5>
# end::class[]
