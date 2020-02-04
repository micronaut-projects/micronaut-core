package io.micronaut.docs.lifecycle;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        final BeanContext context = BeanContext.run();
        Vehicle vehicle = context
                .getBean(Vehicle.class);

        System.out.println(vehicle.start());
        // end::start[]

        assertTrue(vehicle.engine instanceof V8Engine);
        assertTrue(((V8Engine)vehicle.engine).isIntialized());

        context.close();
    }
}
