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
            "my.engine.sensors": {
                0: "thermostat",
                1: "fuel pressure",
            },
            "spec.name": "VehicleMapFormatSpec",
        }

        application_context = ApplicationContext.run(values, "test")

        Vehicle = java.type("micronaut.docs.config.mapFormat.Vehicle")
        vehicle = application_context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert "Engine Starting V8 [sensors=2]" == vehicle.start()
        application_context.close()
