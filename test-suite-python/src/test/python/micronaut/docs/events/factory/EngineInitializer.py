from jakarta.inject import Singleton
from micronaut.context.event import BeanInitializedEventListener, BeanInitializingEvent

from .EngineFactory import EngineFactory


# tag::class[]
@Singleton
class EngineInitializer(BeanInitializedEventListener[EngineFactory]):  # <4>
    def onInitialized(self, event: BeanInitializingEvent[EngineFactory]) -> EngineFactory:
        engine_factory = event.getBean()
        engine_factory.rod_length = 6.6  # <5>
        return engine_factory
# end::class[]
