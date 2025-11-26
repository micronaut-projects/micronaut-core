from org.junit.jupiter.api import Test
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from jakarta.inject import Inject
from typing import Annotated
import java


# tag::class[]
@MicronautTest
class PreDestroySpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_predestroy(self):
        PreDestroyBean = java.type("micronaut.docs.lifecycle.PreDestroyBean")
        bean = self.context.getBean(PreDestroyBean).asPolyglotValue()

        assert bean.stopped == False, "Should not be stopped"

        self.context.stop()

        assert bean.stopped == True, "Should be stopped"
# end::class[]
