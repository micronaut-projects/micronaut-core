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
