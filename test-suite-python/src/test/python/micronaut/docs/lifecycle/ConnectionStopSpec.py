from org.junit.jupiter.api import Test
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from jakarta.inject import Inject
from typing import Annotated
import java


# tag::class[]
@MicronautTest
class ConnectionStopSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_factory_stop(self):
        ConnectionBean = java.type("micronaut.docs.lifecycle.Connection")
        connection = self.context.getBean(ConnectionBean).asPolyglotValue()

        assert connection.stopped == False, "Should not be stopped"

        self.context.stop()

        assert connection.stopped == True, "Should be stopped"
# end::class[]
