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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;

import java.util.Objects;

/**
 * Element that supports adding qualifiers.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface ConfigurableElement extends Element {
    /**
     * Fills the type arguments for this element from the given types.
     * @param types The types
     * @return This element
     */
    @NonNull ConfigurableElement typeArguments(@NonNull ClassElement...types);

    /**
     * Adds a {@link jakarta.inject.Named} qualifier to the element.
     *
     * @param qualifier The qualifier. If {@code null} an named annotation with no value is added assuming the default name.
     * @return This element
     */
    default @NonNull
    ConfigurableElement qualifier(@Nullable String qualifier) {
        return qualifier(AnnotationValue.builder(AnnotationUtil.NAMED).value(qualifier).build());
    }

    /**
     * Adds a qualifier for the given annotation value to the element.
     *
     * @param qualifier The qualifier
     * @return This element
     */
    default @NonNull
    ConfigurableElement qualifier(@NonNull AnnotationValue<?> qualifier) {
        Objects.requireNonNull(qualifier, "Qualifier cannot be null");
        annotate(qualifier.getAnnotationName(), (builder) ->
                builder.members(qualifier.getValues())
        );
        return this;
    }
}
