from org.junit.jupiter.api import Test
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from jakarta.inject import Inject
from typing import Annotated
import java

from . import ShutdownLog


@MicronautTest
class DependsOnSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_depends_on_orders_creation_and_destruction(self):
        ShutdownLog.clear()
        MessageConsumer = java.type("micronaut.docs.lifecycle.MessageConsumer")
        self.context.getBean(MessageConsumer)

        assert ShutdownLog.events() == ["publisher created", "consumer created"]

        self.context.stop()

        assert ShutdownLog.events() == [
            "publisher created",
            "consumer created",
            "consumer stopped",
            "publisher closed",
        ]
