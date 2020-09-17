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
package io.micronaut.docs.config.property;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class EngineSpec {

    @Test
    public void testStartVehicleWithConfiguration() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(1);
        map.put("my.engine.cylinders", "8");
        map.put("my.engine.manufacturer", "Honda");
        ApplicationContext ctx = ApplicationContext.run(map);

        Engine engine = ctx.getBean(Engine.class);

        assertEquals("Honda", engine.getManufacturer());
        assertEquals(8, engine.getCylinders());

        ctx.close();
    }
}
