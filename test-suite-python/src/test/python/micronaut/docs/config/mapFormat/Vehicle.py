from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Engine import Engine


@Requires(property="spec.name", value="VehicleMapFormatSpec")
@Singleton
class Vehicle:
    def __init__(self, engine: Engine):
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()

    def get_engine(self) -> Engine:
        return self.engine
