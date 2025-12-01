package io.micronaut.docs.ioc.injection.optional;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Property(name = "spec.name", value = "VehicleIocInjectionOptionalSpec")
@MicronautTest(startApplication = false)
@Disabled("optional injection is not supported")
class OptionalAutowiredTest {
    @Test
    void testVehicle(Vehicle vehicle) {
        Assertions.assertEquals(6, vehicle.getEngine().cylinders());
    }
}
