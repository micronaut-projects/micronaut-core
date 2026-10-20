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
package io.micronaut.inject.executable.reuse;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The generated methods of a bean definition are shared by the contexts of the JVM: each context resolves their
 * property placeholders with its own environment.
 */
class ContextReuseTest {

    @Test
    void eachContextResolvesThePlaceholdersOfAMethodWithItsEnvironment() {
        for (String label : new String[] {"first", "second", "third"}) {
            try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "ContextReuseTest", "reuse.label", label))) {
                context.getBean(LabeledBean.class);
                ExecutableMethod<LabeledBean, Object> method = context.getBeanDefinition(LabeledBean.class).getRequiredMethod("labeled");

                assertEquals(label, method.stringValue(Labeled.class).orElse(null));
            }
        }
    }
}
