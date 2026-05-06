from .CrankShaft import CrankShaft
from .Engine import Engine
from .SparkPlug import SparkPlug


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
    def builder():
        return EngineImpl.Builder()

    class Builder:
        manufacturer: str = "Ford"
        cylinders: int = 0

        def withManufacturer(self, manufacturer: str):
            self.manufacturer = manufacturer
            return self

        def withCylinders(self, cylinders: int):
            self.cylinders = cylinders
            return self

        def build(self, crank_shaft: CrankShaft.Builder, spark_plug: SparkPlug.Builder):
            return EngineImpl(
                self.manufacturer,
                self.cylinders,
                crank_shaft.build(),
                spark_plug.build(),
            )
# end::class[]


Builder = EngineImpl.Builder
