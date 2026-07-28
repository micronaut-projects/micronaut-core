from jakarta.inject import Singleton, Named
from .Engine import Engine
from .V8 import V8

@Singleton
class Vehicle:
    # tag::constructor[]
    def __init__(self, engine: Engine[V8]):
        self.engine = engine
    # end::constructor[]

    def start(self) -> str:
        return self.engine.start()
