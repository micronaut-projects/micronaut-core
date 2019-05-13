package io.micronaut.docs.qualifiers.annotation;

import io.micronaut.context.DefaultBeanContext;
import io.micronaut.docs.inject.qualifiers.named.Vehicle;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        io.micronaut.docs.inject.qualifiers.named.Vehicle vehicle = new DefaultBeanContext().start().getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Starting V8", vehicle.start());
    }

}