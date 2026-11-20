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
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.builders.Sample;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Values kept by the methods of contexts that are running at the same time.
 */
class CachedValuesContextsTest {

    record Owner(ApplicationContext context) {
    }

    record Name(String value) {
    }

    @Test
    void eachContextKeepsItsOwnValues() {
        try (ApplicationContext a = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a", "cached.plan.value", "a"));
             ApplicationContext b = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "b", "cached.plan.value", "b"))) {
            ExecutableMethod<PlainBean, Object> methodA = method(a);
            ExecutableMethod<PlainBean, Object> methodB = method(b);
            assertNotSame(methodA, methodB);

            // Alternating between the contexts, each one resolves and keeps its own properties
            for (int i = 0; i < 3; i++) {
                assertEquals("a", methodA.getCachedValue(Name.class, m -> new Name(m.stringValue(Sample.class, "name").orElseThrow())).value());
                assertEquals("b", methodB.getCachedValue(Name.class, m -> new Name(m.stringValue(Sample.class, "name").orElseThrow())).value());
                assertEquals("a", methodA.getCachedAnnotation(Sample.class).name());
                assertEquals("b", methodB.getCachedAnnotation(Sample.class).name());
                assertEquals("a", a.getBean(PlannedBean.class).placeholder());
                assertEquals("b", b.getBean(PlannedBean.class).placeholder());
            }

            // A value may hold what the context owns
            assertSame(a, methodA.getCachedValue(Owner.class, m -> new Owner(a)).context());
            assertSame(b, methodB.getCachedValue(Owner.class, m -> new Owner(b)).context());
        }
    }

    @Test
    void aRestartedContextComputesItsValuesAgain() {
        Object first;
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a"))) {
            first = method(context).getCachedValue(Object.class, m -> new Object());
        }
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest", "cached.sample.name", "a"))) {
            assertNotSame(first, method(context).getCachedValue(Object.class, m -> new Object()));
        }
    }

    private static ExecutableMethod<PlainBean, Object> method(ApplicationContext context) {
        context.getBean(PlainBean.class);
        return context.getBeanDefinition(PlainBean.class).getRequiredMethod("placeholderAnnotated");
    }
}
