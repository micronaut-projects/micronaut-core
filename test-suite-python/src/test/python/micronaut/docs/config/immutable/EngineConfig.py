from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.core.bind.annotation import Bindable
from micronaut.context.annotation import ConfigurationInject
from micronaut.context.annotation import ConfigurationProperties

from jakarta.validation.constraints import Min
from jakarta.validation.constraints import NotBlank
from jakarta.validation.constraints import NotNull
# end::imports[]


@Requires(property="spec.name", value="VehicleImmutableSpec")
# tag::class[]
@ConfigurationProperties("my.engine")  # <1>
class EngineConfig:
    @ConfigurationProperties("crank-shaft")
    class CrankShaft:  # <5>
        @ConfigurationInject
        def __init__(self, rod_length: float | None = None):  # <6>
            self.rod_length = rod_length

        def get_rod_length(self) -> float | None:
            return self.rod_length

    @ConfigurationInject  # <2>
    def __init__(
        self,
        manufacturer: Annotated[str, Bindable(defaultValue="Ford"), NotBlank],  # <3>
        cylinders: Annotated[int, Min(1)],  # <4>
        crank_shaft: Annotated[CrankShaft, NotNull],
    ):
        self.manufacturer = manufacturer
        self.cylinders = cylinders
        self.crank_shaft = crank_shaft

    def get_manufacturer(self) -> str:
        return self.manufacturer

    def get_cylinders(self) -> int:
        return self.cylinders

    def get_crank_shaft(self) -> CrankShaft:
        return self.crank_shaft
# end::class[]


CrankShaft = EngineConfig.CrankShaft
