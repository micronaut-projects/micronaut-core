from jakarta.inject import Singleton
from .Engine import Engine

# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine: Engine): # <3>
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()
# end::class[]
