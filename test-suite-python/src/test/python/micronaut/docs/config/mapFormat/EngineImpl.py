from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Requires

from .Engine import Engine
from .EngineConfig import EngineConfig


@Requires(property="spec.name", value="VehicleMapFormatSpec")
# tag::class[]
@Singleton
class EngineImpl(Engine):
    config: Annotated[EngineConfig, Inject]

    def get_sensors(self):
        return self.config.sensors

    def start(self) -> str:
        return (
            f"Engine Starting V{self.config.cylinders} "
            f"[sensors={len(self.get_sensors())}]"
        )

    def get_config(self) -> EngineConfig:
        return self.config

    def set_config(self, config: EngineConfig) -> None:
        self.config = config
# end::class[]
