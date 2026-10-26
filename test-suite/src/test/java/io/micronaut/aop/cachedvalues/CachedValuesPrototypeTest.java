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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Values kept by the methods of the proxies of a non-singleton bean.
 */
class CachedValuesPrototypeTest {

    @Test
    void theProxiesOfAContextShareTheMethodsAndTheirValues() {
        try (ApplicationContext a = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest"));
             ApplicationContext b = ApplicationContext.run(Map.of("spec.name", "CachedValuesTest"))) {
            RecordingInterceptor recordedA = work(a);
            RecordingInterceptor recordedB = work(b);

            assertEquals(2, recordedA.methods.size());
            assertSame(recordedA.methods.get(0), recordedA.methods.get(1));
            assertSame(recordedA.values.get(0), recordedA.values.get(1));
            assertSame(recordedB.methods.get(0), recordedB.methods.get(1));
            assertSame(recordedB.values.get(0), recordedB.values.get(1));

            // Each context keeps its own methods, and the values they keep
            assertNotSame(recordedA.methods.get(0), recordedB.methods.get(0));
            assertNotSame(recordedA.values.get(0), recordedB.values.get(0));
        }
    }

    private static RecordingInterceptor work(ApplicationContext context) {
        RecordedPrototype first = context.getBean(RecordedPrototype.class);
        RecordedPrototype second = context.getBean(RecordedPrototype.class);
        assertNotSame(first, second);
        first.work();
        second.work();
        return context.getBean(RecordingInterceptor.class);
    }
}
