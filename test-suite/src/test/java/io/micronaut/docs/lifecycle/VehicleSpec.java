package io.micronaut.docs.lifecycle;

import io.micronaut.context.DefaultBeanContext;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;
import spock.lang.Specification;

import static org.junit.Assert.assertTrue;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        Vehicle vehicle = new DefaultBeanContext().start().getBean(Vehicle.class);

        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertTrue(vehicle.getEngine() instanceof V8Engine);
        assertTrue(((V8Engine) vehicle.getEngine()).getInitialized());
    }

}
