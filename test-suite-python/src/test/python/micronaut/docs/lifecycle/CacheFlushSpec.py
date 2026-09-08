from org.junit.jupiter.api import Test
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from jakarta.inject import Inject
from typing import Annotated
import java


# tag::class[]
@MicronautTest
class CacheFlushSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_class_predestroy(self):
        CacheBean = java.type("micronaut.docs.lifecycle.Cache")
        cache = self.context.getBean(CacheBean).asPolyglotValue()

        assert cache.flushed == False, "Should not be flushed"

        self.context.stop()

        assert cache.flushed == True, "Should be flushed"
# end::class[]
