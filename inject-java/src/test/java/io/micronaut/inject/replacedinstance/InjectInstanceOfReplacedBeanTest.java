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
package io.micronaut.inject.replacedinstance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InjectInstanceOfReplacedBeanTest {

    @Test
    void injectingAnInstanceWhoseRuntimeDefinitionReplacesTheCompiledOne() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "InjectInstanceOfReplacedBeanTest"))) {
            Injected instance = new Injected();
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Injected.class, () -> instance)
                .singleton(true)
                .replaces(Injected.class)
                .build());
            // a lookup of the type sees only the replacement
            assertSame(instance, context.getBean(Injected.class));
            assertEquals(List.of(instance), List.copyOf(context.getBeansOfType(Injected.class)));
            // so does a lookup of the candidates for the instance
            assertDoesNotThrow(() -> context.inject(instance));
        }
    }

    @Requires(property = "spec.name", value = "InjectInstanceOfReplacedBeanTest")
    @Singleton
    static class Injected {
        @Inject
        Dependency dependency;
    }

    @Requires(property = "spec.name", value = "InjectInstanceOfReplacedBeanTest")
    @Singleton
    static class Dependency {
    }
}
