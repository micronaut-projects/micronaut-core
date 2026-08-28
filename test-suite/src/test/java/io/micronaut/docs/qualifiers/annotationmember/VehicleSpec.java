package io.micronaut.docs.qualifiers.annotationmember;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        final ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "VehicleAnnotationMemberSpec"));
        Vehicle vehicle = context.getBean(Vehicle.class);
        System.out.println(vehicle.start());
        // end::start[]

        assertEquals("Starting V8", vehicle.start());
        context.close();
    }

}
