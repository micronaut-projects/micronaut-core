package io.micronaut.docs.events.factory;

import io.micronaut.context.DefaultBeanContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        Vehicle vehicle = new DefaultBeanContext()
                .start()
                .getBean(Vehicle.class);
        System.out.println( vehicle.start() );
        // end::start[]

        assertEquals("Starting V8 [rodLength=6.6]", vehicle.start());
    }

}
