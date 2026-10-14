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
package docs.devmode;

import io.micronaut.context.RuntimeBeanDefinition;

import java.util.function.Supplier;

/**
 * Bean definitions the Python dev-mode docs tests register at runtime, which a Python test cannot build itself since
 * {@link RuntimeBeanDefinition#builder(Class, Supplier)} has an overload taking a function.
 */
public final class RuntimeDefinitions {

    private RuntimeDefinitions() {
    }

    /**
     * @param beanType The bean type
     * @param name The name qualifier
     * @param <T> The bean type
     * @return A singleton definition whose bean is never created: a definition watch reads the definition only
     */
    public static <T> RuntimeBeanDefinition<T> neverCreated(Class<T> beanType, String name) {
        Supplier<T> supplier = () -> {
            throw new UnsupportedOperationException("never created: the watch reads definitions only");
        };
        return RuntimeBeanDefinition.builder(beanType, supplier).named(name).singleton(true).build();
    }
}
