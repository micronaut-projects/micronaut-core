# tag::imports[]
from micronaut.docs.context.events.SampleEvent import SampleEvent
from micronaut.context.event import StartupEvent, ShutdownEvent
from micronaut.runtime.event.annotation import EventListener
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
    def on_sample_event(self, event: SampleEvent) -> None:
        self.invocation_counter += 1

    # @EventListener
    def on_startup_event(self, event: StartupEvent) -> None:
        pass

    # @EventListener
    def on_shutdown_event(self, event: ShutdownEvent) -> None:
        pass

    def get_invocation_counter(self) -> int:
        return self.invocation_counter
# end::class[]
