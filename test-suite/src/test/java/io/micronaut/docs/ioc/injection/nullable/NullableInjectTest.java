package io.micronaut.docs.ioc.injection.nullable;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest(startApplication = false)
public class NullableInjectTest {
    @Test
    void testVehicle(Vehicle vehicle) {
        Assertions.assertEquals(6, vehicle.getEngine().cylinders());
    }
}
