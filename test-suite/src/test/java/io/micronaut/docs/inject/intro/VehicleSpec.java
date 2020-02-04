package io.micronaut.docs.inject.intro;

import io.micronaut.context.BeanContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        final BeanContext context = BeanContext.run();
        Vehicle vehicle = context.getBean(Vehicle.class);
        System.out.println(vehicle.start());
        // end::start[]

        assertEquals("Starting V8", vehicle.start());

        context.close();
    }

}
