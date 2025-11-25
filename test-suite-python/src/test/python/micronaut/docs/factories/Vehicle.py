from .Engine import Engine
from jakarta.inject import Singleton

# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine : Engine):
        self.engine = engine

    def start(self) -> str:
        self.engine.start()
# end::class[]
