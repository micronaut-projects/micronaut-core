# tag::class[]
from jakarta.inject import Singleton
from micronaut.docs.context.events.SampleEvent import SampleEvent

@Singleton
class SampleEventListener:
    invocation_count: int = 0

    # TODO: Re-enable this direct port when Python ApplicationEventListener
    # generic adaptation compiles.
    # class SampleEventListener(ApplicationEventListener[SampleEvent]):
    #     def onApplicationEvent(self, event: SampleEvent):
    #         self.invocation_count += 1
# end::class[]
