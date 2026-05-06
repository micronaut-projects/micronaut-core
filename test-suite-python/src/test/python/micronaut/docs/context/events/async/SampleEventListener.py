# tag::imports[]
from micronaut.docs.context.events.SampleEvent import SampleEvent
from micronaut.runtime.event.annotation import EventListener
from micronaut.scheduling.annotation import Async
# end::imports[]
from jakarta.inject import Singleton


# tag::class[]
@Singleton
class SampleEventListener:
    def __init__(self) -> None:
        self.invocation_counter = 0

    # TODO: Re-enable @EventListener when Python event listener method
    # adaptation with event arguments compiles.
    # @EventListener
    @Async
    def on_sample_event(self, event: SampleEvent) -> None:
        self.invocation_counter += 1

    def get_invocation_counter(self) -> int:
        return self.invocation_counter
# end::class[]
