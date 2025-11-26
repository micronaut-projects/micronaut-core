from micronaut.context.annotation import Factory, EachBean
from micronaut.context.exceptions import DisabledBeanException
from .EngineConfiguration import EngineConfiguration
from .Engine import Engine
import java

DisabledBeanException = java.type("io.micronaut.context.exceptions.DisabledBeanException")

# tag::class[]
@Factory
class EngineFactory:
    @EachBean(EngineConfiguration.__qualname__)
    def build_engine(self, config : EngineConfiguration) -> Engine:
        if config.enabled:
            return Engine(config.cylinders)
        else:
            raise DisabledBeanException("Engine configuration disabled")

# end::class[]
