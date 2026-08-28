from typing import Annotated

from .CrankShaft import CrankShaft
from .CrankShaft import CrankShaftBuilder
from .EngineImpl import EngineImpl
from .EngineImpl import EngineImplBuilder
from .SparkPlug import SparkPlug
from .SparkPlug import SparkPlugBuilder

# tag::imports[]
from micronaut.context.annotation import ConfigurationBuilder
from micronaut.context.annotation import ConfigurationProperties
# end::imports[]


# tag::class[]
@ConfigurationProperties("my.engine")  # <1>
class EngineConfig:
    builder: Annotated[EngineImplBuilder, ConfigurationBuilder(prefixes="with")] = EngineImpl.builder()  # <2>

    crank_shaft: Annotated[
        CrankShaftBuilder,
        ConfigurationBuilder(prefixes="with", configurationPrefix="crank-shaft"),
    ] = CrankShaft.builder()  # <3>

    _spark_plug: SparkPlugBuilder = SparkPlug.builder()

    @property
    def spark_plug(self) -> SparkPlugBuilder:
        return self._spark_plug

    @spark_plug.setter
    @ConfigurationBuilder(prefixes="with", configurationPrefix="spark-plug")  # <4>
    def spark_plug(self, spark_plug: SparkPlugBuilder) -> None:
        self._spark_plug = spark_plug
# end::class[]
