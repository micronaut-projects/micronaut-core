from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Engine import Engine


@Requires(property="spec.name", value="VehicleBuilderSpec")
# tag::class[]
@Singleton
class Vehicle:
    def __init__(self, engine: Engine):  # <6>
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()
# end::class[]
