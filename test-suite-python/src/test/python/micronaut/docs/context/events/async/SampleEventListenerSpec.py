# tag::imports[]
from typing import Annotated
from jakarta.inject import Inject
from micronaut.docs.context.events.SampleEventEmitterBean import SampleEventEmitterBean
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from time import sleep
from .SampleEventListener import SampleEventListener
# end::imports[]


# tag::class[]
@MicronautTest
class SampleEventListenerSpec:
    emitter: Annotated[SampleEventEmitterBean, Inject]
    listener: Annotated[SampleEventListener, Inject]

    @Test
    def test_event_listener_is_notified(self) -> None:
        assert self.listener.get_invocation_counter() == 0
        self.emitter.publish_sample_event()
        for _ in range(50):
            if self.listener.get_invocation_counter() == 1:
                break
            sleep(0.1)
        assert self.listener.get_invocation_counter() == 1
# end::class[]
