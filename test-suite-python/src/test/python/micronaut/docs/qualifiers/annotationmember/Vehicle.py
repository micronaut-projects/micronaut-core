from jakarta.inject import Singleton, Named
from .Engine import Engine
from typing import Annotated
from .Cylinders import Cylinders

# tag::class[]
@Singleton
class Vehicle:
    # tag::constructor[]
    def __init__(self, engine: Annotated[Engine, Cylinders(value=8)]):
        self.engine = engine
    # end::constructor[]

    def start(self) -> str:
        return self.engine.start() # <5>
# end::class[]
