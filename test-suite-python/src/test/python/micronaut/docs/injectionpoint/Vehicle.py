from .Engine import Engine
from jakarta.inject import Singleton
from .Cylinders import Cylinders
from typing import Annotated

# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine : Annotated[Engine, Cylinders(6)]):
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()
# end::class[]
