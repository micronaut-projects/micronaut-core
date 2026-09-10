/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place the Python processor reads a loaded annotation class: the bare parser (no javac)
 * resolves Java annotation types as loaded classes, so their members can only be read reflectively.
 * Lives in this package because it is the package allowed to read classes (see
 * {@code config/checkstyle/reflection-import-control.xml}).
 *
 * @since 5.2.0
 */
@Internal
public final class AnnotationMemberReflection {

    private AnnotationMemberReflection() {
    }

    /**
     * The declared return type names of an annotation class's members, as
     * {@link Class#getName()} spells them ({@code example.Nested}, {@code [Lexample.Nested;}).
     *
     * @param annotationType The loaded annotation type
     * @return The member return type names
     */
    public static List<String> memberReturnTypeNames(Class<?> annotationType) {
        List<String> names = new ArrayList<>();
        for (Method method : annotationType.getDeclaredMethods()) {
            names.add(method.getReturnType().getName());
        }
        return names;
    }
}
