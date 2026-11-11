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
package io.micronaut.aop.cachedvalues;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Values kept by the bean definitions of contexts that are running at the same time.
 */
class BeanDefinitionCachedValuesContextsTest {

    record Owner(ApplicationContext context) {
    }

    @Test
    void eachContextKeepsItsOwnValues() {
        try (ApplicationContext a = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a"));
             ApplicationContext b = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "b"))) {
            BeanDefinition<PlainBean> definitionA = a.getBeanDefinition(PlainBean.class);
            BeanDefinition<PlainBean> definitionB = b.getBeanDefinition(PlainBean.class);
            assertNotSame(definitionA, definitionB);

            // A value may hold what the context owns
            assertSame(a, definitionA.getCachedValue(Owner.class, d -> new Owner(a)).context());
            assertSame(b, definitionB.getCachedValue(Owner.class, d -> new Owner(b)).context());
            assertSame(a, definitionA.getCachedValue(Owner.class, d -> new Owner(b)).context());
        }
    }

    @Test
    void aRestartedContextComputesItsValuesAgain() {
        Object first;
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a"))) {
            first = context.getBeanDefinition(PlainBean.class).getCachedValue(Object.class, d -> new Object());
        }
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a"))) {
            assertNotSame(first, context.getBeanDefinition(PlainBean.class).getCachedValue(Object.class, d -> new Object()));
        }
    }
}
