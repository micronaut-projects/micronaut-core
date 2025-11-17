from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

@MicronautTest
class VehicleSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    @Disabled("Qualifiers defined in Python not working yet")
    def test_start_vehicle(self) -> None:
        # tag::start[]
        Vehicle = java.type("micronaut.docs.inject.qualifiers.annotation.Vehicle")
        vehicle = self.context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert "Starting V8" == vehicle.start()


