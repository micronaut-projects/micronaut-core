package io.micronaut.docs.config.mapFormat;

import io.micronaut.context.ApplicationContext;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;
import spock.lang.Specification;

import java.io.Serializable;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        LinkedHashMap<String, Object> map = new LinkedHashMap(2);
        map.put("my.engine.cylinders", "8");
        LinkedHashMap<Integer, String> map1 = new LinkedHashMap(2);
        map1.put(0, "thermostat");
        map1.put(1, "fuel pressure");
        map.put("my.engine.sensors", map1);
        ApplicationContext applicationContext = ApplicationContext.run(map, "test");

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Engine Starting V8 [sensors=2]", vehicle.start());
        applicationContext.close();
    }

}
