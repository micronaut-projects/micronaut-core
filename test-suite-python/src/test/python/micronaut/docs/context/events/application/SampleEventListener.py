# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.event import ApplicationEventListener
from micronaut.docs.context.events.SampleEvent import SampleEvent
# end::imports[]

# tag::class[]
@Singleton
class SampleEventListener(ApplicationEventListener[SampleEvent]):
    invocation_count: int = 0

    def onApplicationEvent(self, event: SampleEvent):
        self.invocation_count += 1
# end::class[]
