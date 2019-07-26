package io.micronaut.docs.config.properties;

import io.micronaut.context.ApplicationContext;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;
import spock.lang.Specification;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        LinkedHashMap<String, Object> map = new LinkedHashMap(1);
        map.put("my.engine.cylinders", "8");
        ApplicationContext applicationContext = ApplicationContext.run(map, "test");

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Ford Engine Starting V8 [rodLength=6.0]", vehicle.start());
    }

}
