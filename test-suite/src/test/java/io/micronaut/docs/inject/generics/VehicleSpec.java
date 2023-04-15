package io.micronaut.docs.inject.generics;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;


@MicronautTest
public class VehicleSpec {
    private final Vehicle vehicle;

    public VehicleSpec(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Test
    public void testStartVehicle() {
        assertEquals("Starting V8", vehicle.start());
        assertEquals(Collections.singletonList(6), vehicle.v6Engines
            .stream()
            .map(Engine::getCylinders)
            .collect(Collectors.toList()));
    }

}
