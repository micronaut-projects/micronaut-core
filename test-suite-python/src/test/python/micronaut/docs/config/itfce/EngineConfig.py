from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import ConfigurationProperties
from micronaut.context.annotation import Requires
from micronaut.core.bind.annotation import Bindable

from jakarta.validation.constraints import Min
from jakarta.validation.constraints import NotBlank
from jakarta.validation.constraints import NotNull
# end::imports[]


@Requires(property="spec.name", value="VehicleItfceSpec")
# tag::class[]
@ConfigurationProperties("my.engine")  # <1>
class EngineConfig(ABC):
    @abstractmethod
    def getManufacturer(self) -> Annotated[str, Bindable(defaultValue="Ford"), NotBlank]:  # <2> <3>
        ...

    @abstractmethod
    def getCylinders(self) -> Annotated[int, Min(1)]:
        ...

    @abstractmethod
    def getCrankShaft(self) -> Annotated["CrankShaft", NotNull]:  # <4>
        ...

    @ConfigurationProperties("crank-shaft")
    class CrankShaft(ABC):  # <5>
        @abstractmethod
        def getRodLength(self) -> float | None:  # <6>
            ...
# end::class[]


CrankShaft = EngineConfig.CrankShaft
