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
package io.micronaut.docs.config.properties;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        Map<String, Object> map = new LinkedHashMap<>(1);
        map.put("my.engine.cylinders", "8");
        ApplicationContext applicationContext = ApplicationContext.run(map, "test");

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        System.out.println(vehicle.start());
        // end::start[]

        assertEquals("Ford Engine Starting V8 [rodLength=6.0]", vehicle.start());
        applicationContext.close();
    }
}
