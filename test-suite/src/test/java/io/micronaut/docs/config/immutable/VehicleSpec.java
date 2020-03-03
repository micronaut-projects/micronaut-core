package io.micronaut.docs.config.immutable;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.core.util.CollectionUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.run(CollectionUtils.mapOf(
                "my.engine.cylinders", "8",
                "my.engine.crank-shaft.rod-length", "7.0"
        ));

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        System.out.println(vehicle.start());
        // end::start[]

        assertEquals("Ford Engine Starting V8 [rodLength=7.0]", vehicle.start());
        applicationContext.close();
    }

    @Test
    public void testStartWithInvalidValue() {
        ApplicationContext applicationContext = null;
        try {
            applicationContext = ApplicationContext.run(CollectionUtils.mapOf(
                    "my.engine.cylinders", "-10",
                    "my.engine.crank-shaft.rod-length", "7.0"
            ));

            Vehicle vehicle = applicationContext.getBean(Vehicle.class);
            fail("Should have failed with a validation error");
        } catch (DependencyInjectionException e) {
            if (applicationContext != null) {
                applicationContext.close();
            }
            assertTrue(
                    e.getCause().getMessage().contains("EngineConfig.cylinders - must be greater than or equal to 1")
            );

        }
    }
}

