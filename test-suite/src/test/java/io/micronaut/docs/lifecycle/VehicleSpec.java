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
package io.micronaut.docs.lifecycle;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VehicleSpec {

    @Test
    public void testStartVehicle() {
        // tag::start[]
        final BeanContext context = BeanContext.run();
        Vehicle vehicle = context
                .getBean(Vehicle.class);

        System.out.println(vehicle.start());
        // end::start[]

        assertTrue(vehicle.engine instanceof V8Engine);
        assertTrue(((V8Engine)vehicle.engine).isIntialized());

        context.close();
    }
}
