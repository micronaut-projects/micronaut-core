from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
# from micronaut.context.exceptions import NoSuchBeanException
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
        V8Engine = java.type("micronaut.docs.inject.typed.V8Engine")
        Engine = java.type("micronaut.docs.inject.typed.Engine")
        try:
            self.context.getBean(V8Engine)
            assert False # should not get here
        except NoSuchBeanException:
            assert True

        assert self.context.getBean(Engine) is not None
        # end::start[]

# end::class[]
