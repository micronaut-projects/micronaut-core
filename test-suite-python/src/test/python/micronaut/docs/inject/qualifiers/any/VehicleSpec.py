from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.docs.inject.qualifiers.named import Engine
from .Vehicle import Vehicle

# tag::imports[]
from jakarta.inject import Inject
from micronaut.context.annotation import Any
from typing import Annotated
# end::imports[]

@MicronautTest
@Disabled("Generic types not represented in the AST yet")
class VehicleSpec:
    # tag::any[]
    engine : Annotated[Engine, Inject, Any] = None
    # end::any[]

    @Test
    def test_engine(self):
        assert self.engine != None


    @Test
    def test_vehicle(self, vehicle: Vehicle):
        vehicle.startAll()
