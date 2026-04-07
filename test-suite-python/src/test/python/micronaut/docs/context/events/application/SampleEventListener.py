# tag::class[]
from jakarta.inject import Singleton, Inject
from micronaut.context.event import ApplicationEventListener
from typing import Annotated
from micronaut.docs.context.events.SampleEvent import SampleEvent

@Singleton
class SampleEventListener(ApplicationEventListener[SampleEvent]):
    invocation_count : int = 0

    def onApplicationEvent(self, event : SampleEvent):
        self.invocation_count += 1
# end::class[]
