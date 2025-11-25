from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

NoSuchBeanException = java.type("io.micronaut.context.exceptions.NoSuchBeanException")

# tag::class[]
@MicronautTest
class EngineSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_engine(self) -> None:
        # tag::start[]
        V8Engine = java.type("micronaut.docs.lifecycle.V8Engine")
        engine = self.context.getBean(V8Engine).asPolyglotValue()
        engine.start()
        assert engine.initialized, "Engine should have been intialized"
        # end::start[]

# end::class[]
