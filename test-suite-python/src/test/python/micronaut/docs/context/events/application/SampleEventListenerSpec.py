# tag::imports[]
from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
# end::imports[]

from jakarta.inject import Inject
from typing import Annotated


from micronaut.docs.context.events.application.SampleEventListener import SampleEventListener
from micronaut.docs.context.events.SampleEventEmitterBean import SampleEventEmitterBean

# tag::class[]
@MicronautTest
@Disabled("GR-71497 - generics can't be passed to types")
class SampleEventListenerSpec:
    listener : Annotated[SampleEventListener, Inject]
    emitter : Annotated[SampleEventEmitterBean, Inject]

    @Test
    def test_event_listener_is_notified(self):
        assert self.listener.invocation_count == 0
        self.emitter.publish_sample_event()
        assert self.listener.invocation_count == 1

# end::class[]
