import java

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test


@MicronautTest
@Disabled("Python immutable @ConfigurationProperties nested constructor argument still resolves as Object")
class VehicleSpec:
    @Test
    def test_start_vehicle(self) -> None:
        # tag::start[]
        application_context = ApplicationContext.run({
            "spec.name": "VehicleImmutableSpec",
            "my.engine.cylinders": "8",
            "my.engine.crank-shaft.rod-length": "7.0",
        })

        Vehicle = java.type("micronaut.docs.config.immutable.Vehicle")
        vehicle = application_context.getBean(Vehicle).asPolyglotValue()
        print(vehicle.start())
        # end::start[]

        assert "Ford Engine Starting V8 [rodLength=7.0]" == vehicle.start()
        application_context.close()
