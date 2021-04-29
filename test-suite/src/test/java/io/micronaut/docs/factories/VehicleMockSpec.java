package io.micronaut.docs.factories;

// tag::imports[]
import io.micronaut.context.annotation.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
// end::imports[]

// tag::class[]
@MicronautTest
public class VehicleMockSpec {
    @Requires(beans=VehicleMockSpec.class)
    @Bean @Replaces(Engine.class) Engine mockEngine = () -> "Mock Started"; // <1>

    @Inject Vehicle vehicle; // <2>

    @Test
    void testStartEngine() {
        final String result = vehicle.start();
        assertEquals("Mock Started", result); // <3>
    }
}
// end::class[]