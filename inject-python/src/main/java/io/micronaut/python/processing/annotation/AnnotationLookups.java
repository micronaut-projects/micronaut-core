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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.ElementDef;

import java.util.Optional;

/**
 * What the annotation value and interceptor-binding helpers need from the metadata builder.
 *
 * @since 5.2.0
 */
@Internal
interface AnnotationLookups {

    /**
     * @param className A class name in source form
     * @return The binary class name
     */
    String binaryClassName(String className);

    /**
     * @param annotationName An annotation name
     * @return The Python decorator definition, or null when the annotation is not Python-defined
     */
    @Nullable DecoratorDef decoratorDef(String annotationName);

    /**
     * @param annotationName An annotation name
     * @return The annotation type element, if resolvable
     */
    Optional<ElementDef> annotationMirror(String annotationName);

    /**
     * @param decorator A decorator
     * @return The Java annotation type it names, or null
     */
    @Nullable ClassElement javaAnnotationType(DecoratorDef decorator);

    /**
     * @param decorator A decorator application
     * @return Its annotation value
     */
    AnnotationValue<?> annotationValue(DecoratorDef decorator);
}
