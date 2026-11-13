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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Experimental;

import java.util.Map;

/**
 * Declares the containers of repeatable annotations whose types are not meta-annotated with
 * {@link java.lang.annotation.Repeatable}: a constraint predating it and held in a nested {@code List} annotation,
 * or an annotation a remapper produces several times on one element. It is loaded as a service by the annotation
 * metadata builder of every compilation.
 *
 * <p>The occurrences held in a declared container are processed one by one, as the compiler unwraps the container
 * of a {@code @Repeatable} annotation, so the remappers, transformers and stereotypes apply to each. Every occurrence
 * is stored under the container, so {@link io.micronaut.core.annotation.AnnotationMetadata#getAnnotationValuesByType(Class)}
 * and {@link io.micronaut.core.annotation.AnnotationMetadata#findRepeatableAnnotation(String)} answer for the
 * annotation, and removing the annotation from an element removes its container. Another annotation holding the
 * same annotation in its value is left as it is.</p>
 *
 * <p>A {@code @Repeatable} the annotation type declares itself wins.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RepeatableAnnotationContainerProvider {

    /**
     * @return The container annotation name by repeatable annotation name
     */
    Map<String, String> getRepeatableContainers();
}
