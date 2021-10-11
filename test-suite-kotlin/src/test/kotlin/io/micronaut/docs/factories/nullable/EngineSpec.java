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
package io.micronaut.docs.factories.nullable;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EngineSpec {

    @Test
    public void testEngineNull() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("engines.subaru.cylinders", 4);
        configuration.put("engines.ford.cylinders", 8);
        configuration.put("engines.ford.enabled", false);
        configuration.put("engines.lamborghini.cylinders", 12);
        ApplicationContext applicationContext = ApplicationContext.run(configuration);

        Collection<Engine> engines = applicationContext.getBeansOfType(Engine.class);

        assertEquals("There are 2 engines", 2, engines.size());
        int totalCylinders = engines.stream().mapToInt(Engine::getCylinders).sum();
        assertEquals("Subaru + Lamborghini equals 16 cylinders", 16, totalCylinders);
    }
}
