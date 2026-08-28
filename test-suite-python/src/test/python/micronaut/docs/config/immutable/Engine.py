from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .EngineConfig import EngineConfig


@Requires(property="spec.name", value="VehicleImmutableSpec")
# tag::class[]
@Singleton
class Engine:
    def __init__(self, config: EngineConfig):  # <1>
        self.config = config

    def get_cylinders(self) -> int:
        return self.config.get_cylinders()

    def start(self) -> str:  # <2>
        rod_length = self.config.get_crank_shaft().get_rod_length()
        if rod_length is None:
            rod_length = 6.0
        return (
            f"{self.config.get_manufacturer()} Engine Starting V{self.config.get_cylinders()} "
            f"[rodLength={rod_length}]"
        )

    def get_config(self) -> EngineConfig:
        return self.config
# end::class[]
