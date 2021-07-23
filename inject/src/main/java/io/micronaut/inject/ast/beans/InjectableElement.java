/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast.beans;

import io.micronaut.context.annotation.Value;

/**
 * Shared interface for injectable elements.
 *
 * @since 3.0.0
 * @author graemerocher
 */
public interface InjectableElement extends ConfigurableElement {
    /**
     * Allows the field to resolve a value with {@link Value}.
     *
     * @param expression The expression to inject
     * @return This field
     */
    default InjectableElement injectValue(String expression) {
        annotate(Value.class, (builder) -> builder.value(expression));
        return this;
    }
}
