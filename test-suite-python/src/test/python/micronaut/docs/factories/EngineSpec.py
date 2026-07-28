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
        Engine = java.type("micronaut.docs.factories.Engine")
        assert self.context.getBean(Engine) is not None
        # end::start[]

# end::class[]
