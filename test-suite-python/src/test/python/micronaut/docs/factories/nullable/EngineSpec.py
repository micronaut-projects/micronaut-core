from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from jakarta.inject import Inject
from typing import Annotated
import java

# tag::class[]
@MicronautTest
@Property(name = "engines.subaru.cylinders", value = 4)
@Property(name = "engines.ford.cylinders", value = 8)
@Property(name = "engines.ford.enabled", value = False)
@Property(name = "engines.lamborghini.cylinders", value = 12)
class EngineSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    @Disabled("GR-71643: There is a bug in GraalPy with interop with Java exceptions")
    def test_engine(self):
        # tag::start[]
        Engine = java.type("micronaut.docs.factories.nullable.Engine")
        engines = self.context.getBeansOfType(Engine)
        assert engines.size() == 2, "There should be 2 engines"
        # end::start[]

# end::class[]
