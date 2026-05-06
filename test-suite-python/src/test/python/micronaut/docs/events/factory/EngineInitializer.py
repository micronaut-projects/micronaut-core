from jakarta.inject import Singleton
from micronaut.context.event import BeanInitializedEventListener, BeanInitializingEvent

from .EngineFactory import EngineFactory


# tag::class[]
@Singleton
class EngineInitializer:  # <4>
    # TODO: Re-enable this direct port when Python BeanInitializedEventListener
    # generic adaptation generates a valid bean definition.
    # class EngineInitializer(BeanInitializedEventListener[EngineFactory]):
    def onInitialized(self, event: BeanInitializingEvent[EngineFactory]) -> EngineFactory:
        engine_factory = event.getBean()
        engine_factory.rod_length = 6.6  # <5>
        return engine_factory
# end::class[]
