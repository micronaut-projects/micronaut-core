from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

@MicronautTest
class VehicleSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_start_vehicle(self) -> None:
        # tag::start[]
        Vehicle = java.type("micronaut.docs.injectionpoint.Vehicle")
        vehicle = self.context.getBean(Vehicle).asPolyglotValue()
        result = vehicle.start()
        print(result)
        # end::start[]

        assert "Starting V6" == vehicle.start(), f"{result} should equal Starting V6"

