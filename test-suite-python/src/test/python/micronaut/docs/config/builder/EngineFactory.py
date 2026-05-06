from .EngineConfig import EngineConfig
from .Engine import Engine

# tag::imports[]
from micronaut.context.annotation import Factory

from jakarta.inject import Singleton
# end::imports[]


# tag::class[]
@Factory
class EngineFactory:
    @Singleton
    def build_engine(self, engine_config: EngineConfig) -> Engine:
        return engine_config.builder.build(
            engine_config.crank_shaft,
            engine_config.get_spark_plug(),
        )
# end::class[]
