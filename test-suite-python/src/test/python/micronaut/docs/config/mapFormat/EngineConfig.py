from typing import Annotated
import java

# tag::imports[]
from micronaut.context.annotation import ConfigurationProperties
from micronaut.context.annotation import Requires
from micronaut.core.convert.format import MapFormat

from jakarta.validation.constraints import Min
# end::imports[]


MapTransformation = java.type("io.micronaut.core.convert.format.MapFormat$MapTransformation")


@Requires(property="spec.name", value="VehicleMapFormatSpec")
# tag::class[]
@ConfigurationProperties("my.engine")
class EngineConfig:
    cylinders: Annotated[int, Min(1)]

    sensors: Annotated[dict[int, str], MapFormat(transformation=MapTransformation.FLAT)]  # <1>
# end::class[]
