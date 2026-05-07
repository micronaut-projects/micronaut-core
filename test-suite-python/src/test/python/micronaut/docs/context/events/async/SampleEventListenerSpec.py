# tag::imports[]
from micronaut.context import ApplicationContext
from micronaut.docs.context.events.SampleEventEmitterBean import SampleEventEmitterBean
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test
from .SampleEventListener import SampleEventListener
# end::imports[]


# tag::class[]
@MicronautTest
@Disabled("Python @EventListener method adaptation with event argument types still resolves against Object")
class SampleEventListenerSpec:
    @Test
    def test_event_listener_is_notified(self) -> None:
        context = ApplicationContext.run()
        try:
            emitter = context.getBean(SampleEventEmitterBean)
            listener = context.getBean(SampleEventListener)
            assert listener.get_invocation_counter() == 0
            emitter.publish_sample_event()
            assert listener.get_invocation_counter() == 1
        finally:
            context.close()
# end::class[]
