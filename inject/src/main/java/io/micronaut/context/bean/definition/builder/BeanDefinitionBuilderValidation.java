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
package io.micronaut.context.bean.definition.builder;

import io.micronaut.core.util.ArgumentUtils;

import java.util.List;

final class BeanDefinitionBuilderValidation {

    private BeanDefinitionBuilderValidation() {
    }

    static <T> T requireNonNull(T value, String name) {
        return ArgumentUtils.requireNonNull(name, value);
    }

    static <T> List<T> requireNonNullElements(List<T> values, String name) {
        requireNonNull(values, name);
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                throw new NullPointerException("Argument [" + name + "] cannot contain null element at index " + i);
            }
        }
        return values;
    }
}
