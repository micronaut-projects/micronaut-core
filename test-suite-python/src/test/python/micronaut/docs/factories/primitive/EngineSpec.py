from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.context.exceptions import NoSuchBeanException
from jakarta.inject import Inject
from typing import Annotated
import java

# tag::class[]
@MicronautTest
class EngineSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_engine(self):
        # tag::start[]
        V8Engine = java.type("micronaut.docs.factories.primitive.V8Engine")
        engine = self.context.getBean(V8Engine).asPolyglotValue()
        assert engine.get_cylinders() == 8, "Should have had 8 cylinders"
        # end::start[]

# end::class[]
