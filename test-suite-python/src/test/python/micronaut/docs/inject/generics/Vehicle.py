from jakarta.inject import Singleton, Named
from .Engine import Engine
from .V8 import V8

# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine: Engine[V8]): # <4>
        self.engine = engine

    def start(self) -> str:
        return self.engine.start() # <5>
# end::class[]
