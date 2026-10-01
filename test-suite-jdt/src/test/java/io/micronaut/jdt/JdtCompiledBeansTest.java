/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jdt;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.jdt.beans.DemoConfiguration;
import io.micronaut.jdt.beans.EngineConfig;
import io.micronaut.jdt.beans.Greeter;
import io.micronaut.jdt.beans.InterfaceEngineConfig;
import io.micronaut.jdt.beans.NestedAnnotated;
import io.micronaut.jdt.beans.OrderedRecord;
import io.micronaut.jdt.beans.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs beans whose bean definitions were produced by the Eclipse JDT compiler rather than javac.
 * The sources live in {@code src/jdt/java} and are compiled by the {@code compileJdt} task.
 */
class JdtCompiledBeansTest {

    @Test
    void immutableConfigurationIsBound() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "my.engine.cylinders", 8,
            "my.engine.crank-shaft.rod-length", 4.0d
        ))) {
            Vehicle vehicle = context.getBean(Vehicle.class);
            assertEquals("Ford Engine Starting V8 [rodLength=4.0]", vehicle.start());

            EngineConfig config = context.getBean(EngineConfig.class);
            assertEquals("Ford", config.getManufacturer());
            assertEquals(8, config.getCylinders());
            assertEquals(4.0d, config.getCrankShaft().getRodLength().orElseThrow());
        }
    }

    @Test
    void interfaceConfigurationIsBound() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "my.itfce.engine.cylinders", 6,
            "my.itfce.engine.crank-shaft.rod-length", 5.0d
        ))) {
            InterfaceEngineConfig config = context.getBean(InterfaceEngineConfig.class);
            assertEquals("Ford", config.getManufacturer());
            assertEquals(6, config.getCylinders());
            assertEquals(5.0d, config.getCrankShaft().getRodLength().orElseThrow());
        }
    }

    @Test
    void eachPropertyRecordBindsEveryComponent() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "demos.one.mode", "FAST",
            "demos.one.enabled", true,
            "demos.two.mode", "SLOW",
            "demos.two.enabled", false
        ))) {
            List<DemoConfiguration> demos = context.getBeansOfType(DemoConfiguration.class)
                .stream()
                .sorted(Comparator.comparing(DemoConfiguration::name))
                .toList();

            assertEquals(2, demos.size());
            assertEquals("one", demos.get(0).name());
            assertEquals(DemoConfiguration.Mode.FAST, demos.get(0).mode());
            assertTrue(demos.get(0).enabled());
            assertEquals("two", demos.get(1).name());
            assertEquals(DemoConfiguration.Mode.SLOW, demos.get(1).mode());
            assertFalse(demos.get(1).enabled());
        }
    }

    @Test
    void recordIntrospectionKeepsDeclarationOrder() {
        BeanIntrospection<OrderedRecord> introspection = BeanIntrospection.getIntrospection(OrderedRecord.class);

        assertEquals(
            List.of("zulu", "yankee", "alpha", "mike"),
            introspection.getBeanProperties().stream().map(p -> p.getName()).toList()
        );
        assertEquals(
            List.of("zulu", "yankee", "alpha", "mike"),
            List.of(introspection.getConstructorArguments()).stream().map(a -> a.getName()).toList()
        );

        OrderedRecord instance = introspection.instantiate("z", 1, true, List.of("m"));
        assertEquals("z", instance.zulu());
        assertEquals(1, instance.yankee());
        assertTrue(instance.alpha());
        assertEquals(List.of("m"), instance.mike());
    }

    @Test
    void nestedAnnotationMembersSurvive() {
        try (ApplicationContext context = ApplicationContext.run()) {
            var definition = context.getBeanDefinition(NestedAnnotated.class);
            var requirements = definition.getAnnotationValuesByType(io.micronaut.context.annotation.Requires.class);

            assertEquals(2, requirements.size());
            assertEquals("jdt.enabled", requirements.get(0).stringValue("property").orElseThrow());
            assertEquals("false", requirements.get(0).stringValue("notEquals").orElseThrow());
            assertEquals(
                CharSequence.class.getName(),
                requirements.get(1).classValues("missingBeans")[0].getName()
            );
        }
    }

    @Test
    void aroundAdviceIsApplied() {
        try (ApplicationContext context = ApplicationContext.run()) {
            Greeter greeter = context.getBean(Greeter.class);
            assertEquals("HELLO WORLD", greeter.greet("world"));
            assertEquals("hello world", greeter.whisper("world"));
        }
    }
}
