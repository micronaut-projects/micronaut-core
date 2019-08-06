package io.micronaut.docs.config.value;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.env.PropertySource;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.Test;
import spock.lang.Specification;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {

    @Test
    public void testStartVehicleWithConfiguration() {
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test");
        LinkedHashMap<String, Object> map = new LinkedHashMap(1);
        map.put("my.engine.cylinders", "8");
        applicationContext.getEnvironment().addPropertySource(PropertySource.of("test", map));
        applicationContext.start();

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Starting V8 Engine", vehicle.start());
    }

    @Test
    public void testStartVehicleWithoutConfiguration() {
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test");
        applicationContext.start();

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Starting V6 Engine", vehicle.start());
    }

}
