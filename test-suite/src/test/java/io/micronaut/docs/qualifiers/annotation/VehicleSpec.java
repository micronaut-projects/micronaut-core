package io.micronaut.docs.qualifiers.annotation;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        final BeanContext context = BeanContext.run();
        Vehicle vehicle = context.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Starting V8", vehicle.start());
        context.close();
    }

}