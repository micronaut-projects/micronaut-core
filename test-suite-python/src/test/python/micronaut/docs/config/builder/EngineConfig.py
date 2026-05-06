from typing import Annotated

from .CrankShaft import CrankShaft
from .EngineImpl import EngineImpl
from .SparkPlug import SparkPlug

# tag::imports[]
from micronaut.context.annotation import ConfigurationBuilder
from micronaut.context.annotation import ConfigurationProperties
# end::imports[]


# tag::class[]
@ConfigurationProperties("my.engine")  # <1>
class EngineConfig:
    builder: Annotated[EngineImpl.Builder, ConfigurationBuilder(prefixes="with")] = EngineImpl.builder()  # <2>

    crank_shaft: Annotated[
        CrankShaft.Builder,
        ConfigurationBuilder(prefixes="with", configurationPrefix="crank-shaft"),
    ] = CrankShaft.builder()  # <3>

    spark_plug: SparkPlug.Builder = SparkPlug.builder()

    def get_spark_plug(self) -> SparkPlug.Builder:
        return self.spark_plug

    @ConfigurationBuilder(prefixes="with", configurationPrefix="spark-plug")  # <4>
    def set_spark_plug(self, spark_plug: SparkPlug.Builder) -> None:
        self.spark_plug = spark_plug
# end::class[]
