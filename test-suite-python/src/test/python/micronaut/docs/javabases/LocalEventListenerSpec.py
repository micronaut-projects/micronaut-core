from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
# tag::imports[]
from docs.javabases import EventBus, EventListener
# end::imports[]


# tag::factory[]
def counting_listener(counts: dict[str, int]) -> EventListener:
    class CountingListener(EventListener):  # <1>

        def onEvent(self, event: str) -> None:
            counts["events"] += 1

        def onComplete(self) -> None:
            counts["complete"] += 1

    return CountingListener()  # <2>
# end::factory[]


@MicronautTest
class LocalEventListenerSpec:

    @Test
    def a_class_defined_in_a_test_method_implements_a_java_interface(self):
        received = []

        class CollectingListener(EventListener):

            def onEvent(self, event: str) -> None:
                received.append(event)

            def onComplete(self) -> None:
                received.append("done")

        listener = CollectingListener()
        # EventBus.publish is overloaded on EventListener and Consumer
        assert EventBus.publish(listener, ["a", "b"]) == "listener"
        assert received == ["a", "b", "done"]
        assert listener.describe() == "listener"
        assert isinstance(listener, CollectingListener)
        assert isinstance(listener, EventListener)

    @Test
    def a_class_defined_in_a_factory_function_implements_a_java_interface(self):
        counts = {"events": 0, "complete": 0}

        # tag::publish[]
        EventBus.publish(counting_listener(counts), ["a", "b"])  # <3>
        # end::publish[]

        assert counts == {"events": 2, "complete": 1}

    @Test
    def a_callable_selects_the_consumer_overload(self):
        received = []
        assert EventBus.publish(received.append, ["a"]) == "consumer"
        assert received == ["a"]
