import java

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test


@MicronautTest
@Disabled("Python @ConfigurationBuilder binding does not populate builder methods yet")
class VehicleSpec:
    @Test
    def test_start_vehicle(self) -> None:
        # tag::start[]
        properties = {
            "spec.name": "VehicleBuilderSpec",
            "my.engine.cylinders": "4",
            "my.engine.manufacturer": "Subaru",
            "my.engine.crank-shaft.rod-length": 4,
            "my.engine.spark-plug.name": "6619 LFR6AIX",
            "my.engine.spark-plug.type": "Iridium",
            "my.engine.spark-plug.companyName": "NGK",
        }
        application_context = ApplicationContext.run(properties, "test")

        Vehicle = java.type("micronaut.docs.config.builder.Vehicle")
        vehicle = application_context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert (
            "Subaru Engine Starting V4 [rodLength=4.0, sparkPlug=Iridium(NGK 6619 LFR6AIX)]"
            == vehicle.start()
        )

        application_context.close()
