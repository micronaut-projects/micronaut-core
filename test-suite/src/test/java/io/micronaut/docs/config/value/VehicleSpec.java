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
package io.micronaut.docs.config.value;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VehicleSpec {

    @Test
    void testStartVehicleWithConfiguration() {
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.builder("test").build();
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
    void testStartVehicleWithoutConfiguration() {
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.builder("test").build();
        applicationContext.start();

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        DefaultGroovyMethods.println(this, vehicle.start());
        // end::start[]

        assertEquals("Starting V6 Engine", vehicle.start());
    }

    @Test
    void testStartVehicleWithNonEmptyPlaceholder() {
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.builder("test").build();
        LinkedHashMap<String, Object> map = new LinkedHashMap(1);
        map.put("my.engine.description", "${DESCRIPTION}");
        map.put("DESCRIPTION", "V8 Engine");
        applicationContext.getEnvironment().addPropertySource(PropertySource.of("test", map));
        applicationContext.start();

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        // end::start[]

        assertEquals("V8 Engine", vehicle.getEngine().getDescription());
    }

    @Test
    void testStartVehicleWithEmptyPlaceholder() {
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.builder("test").build();
        LinkedHashMap<String, Object> map = new LinkedHashMap(1);
        map.put("my.engine.description", "${DESCRIPTION}");
        applicationContext.getEnvironment().addPropertySource(PropertySource.of("test", map));
        applicationContext.start();

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        // end::start[]

        assertNull(vehicle.getEngine().getDescription());
    }
}
