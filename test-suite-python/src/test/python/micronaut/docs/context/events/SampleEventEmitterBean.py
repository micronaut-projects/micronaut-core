# tag::class[]
from jakarta.inject import Singleton, Inject
from micronaut.context.event import ApplicationEventPublisher
from typing import Annotated
from .SampleEvent import SampleEvent

@Singleton
class SampleEventEmitterBean:
    event_publisher : Annotated[ApplicationEventPublisher, Inject]
    # TODO: fix generics event_publisher : Annotated[ApplicationEventPublisher[SampleEvent], Inject]

    def publish_sample_event(self):
        self.event_publisher.publishEvent(SampleEvent())
# end::class[]
