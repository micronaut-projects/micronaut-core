from micronaut.context.annotation import Factory
from jakarta.inject import Singleton
from .CrankShaft import CrankShaft
from .Engine import Engine
from .V8Engine import V8Engine

# tag::class[]
@Factory
class EngineFactory:
    @Singleton
    def v8_engine(self, crank_shaft: CrankShaft) -> Engine:
        return V8Engine(crank_shaft)
# end::class[]
