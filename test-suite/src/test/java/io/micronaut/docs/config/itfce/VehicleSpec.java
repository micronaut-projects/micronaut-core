/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.config.itfce;

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
        } catch (BeanInstantiationException e) {
            if (applicationContext != null) {
                applicationContext.close();
            }

            assertTrue(
                    e.getMessage().contains("EngineConfig.getCylinders - must be greater than or equal to 1")
            );

        }
    }
}

