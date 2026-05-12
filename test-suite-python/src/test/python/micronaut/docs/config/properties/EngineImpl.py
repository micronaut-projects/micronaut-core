from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Engine import Engine
from .EngineConfig import EngineConfig


@Requires(property="spec.name", value="VehiclePropertiesSpec")
# tag::class[]
@Singleton
class EngineImpl(Engine):
    def __init__(self, config: EngineConfig):  # <1>
        self.config = config

    def get_cylinders(self) -> int:
        return self.config.cylinders

    def start(self) -> str:  # <2>
        rod_length = self.config.crank_shaft.rod_length
        if rod_length is None:
            rod_length = 6.0
        return (
            f"{self.config.manufacturer} Engine Starting V{self.config.cylinders} "
            f"[rodLength={rod_length}]"
        )

    def get_config(self) -> EngineConfig:
        return self.config
# end::class[]
