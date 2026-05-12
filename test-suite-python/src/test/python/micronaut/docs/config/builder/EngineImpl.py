from .CrankShaft import CrankShaft
from .CrankShaft import CrankShaftBuilder
from .Engine import Engine
from .SparkPlug import SparkPlug
from .SparkPlug import SparkPlugBuilder


# tag::class[]
class EngineImpl(Engine):
    def __init__(
        self,
        manufacturer: str,
        cylinders: int,
        crank_shaft: CrankShaft,
        spark_plug: SparkPlug,
    ):
        self.manufacturer = manufacturer
        self.cylinders = cylinders
        self.crank_shaft = crank_shaft
        self.spark_plug = spark_plug

    def get_cylinders(self) -> int:
        return self.cylinders

    def start(self) -> str:
        rod_length = self.crank_shaft.rod_length
        if rod_length is None:
            rod_length = 6.0
        return (
            f"{self.manufacturer} Engine Starting V{self.cylinders} "
            f"[rodLength={rod_length}, sparkPlug={self.spark_plug}]"
        )

    @staticmethod
    def builder() -> "EngineImplBuilder":
        return EngineImplBuilder()
# end::class[]


class EngineImplBuilder:
    manufacturer: str = "Ford"
    cylinders: int = 0

    def withManufacturer(self, manufacturer: str) -> "EngineImplBuilder":
        self.manufacturer = manufacturer
        return self

    def withCylinders(self, cylinders: int) -> "EngineImplBuilder":
        self.cylinders = cylinders
        return self

    def build(self, crank_shaft: CrankShaftBuilder, spark_plug: SparkPlugBuilder) -> EngineImpl:
        return EngineImpl(
            self.manufacturer,
            self.cylinders,
            crank_shaft.build(),
            spark_plug.build(),
        )


Builder = EngineImplBuilder
EngineImpl.Builder = EngineImplBuilder
