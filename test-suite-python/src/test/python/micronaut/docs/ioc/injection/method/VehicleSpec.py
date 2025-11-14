from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

@MicronautTest
class VehicleSpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_start_vehicle(self):
        # tag::start[]
        Vehicle = java.type("micronaut.docs.ioc.injection.method.Vehicle")
        vehicle = self.context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert "Vrooom!" == vehicle.start()
