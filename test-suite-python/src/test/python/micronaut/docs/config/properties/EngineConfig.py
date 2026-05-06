from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import ConfigurationProperties

from micronaut.context.annotation import Requires
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]


@Requires(property="spec.name", value="VehiclePropertiesSpec")
# tag::class[]
@ConfigurationProperties("my.engine")  # <1>
class EngineConfig:
    @ConfigurationProperties("crank-shaft")
    class CrankShaft:  # <4>
        rod_length: float | None = None  # <5>

    manufacturer: Annotated[str, NotBlank] = "Ford"  # <2> <3>
    cylinders: Annotated[int, Min(1)]
    crank_shaft: CrankShaft = CrankShaft()
# end::class[]


CrankShaft = EngineConfig.CrankShaft
