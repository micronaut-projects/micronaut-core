import java

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class VehicleSpec:
    @Test
    def test_start_vehicle(self) -> None:
        # tag::start[]
        values = {
            "my.engine.cylinders": "8",
            "spec.name": "VehiclePropertiesSpec",
        }
        application_context = ApplicationContext.run(values, "test")

        Vehicle = java.type("micronaut.docs.config.properties.Vehicle")
        vehicle = application_context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert "Ford Engine Starting V8 [rodLength=6.0]" == vehicle.start()
        application_context.close()
