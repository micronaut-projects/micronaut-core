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

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Builds annotations with the {@link AnnotationBuilder} registered for their type, never reflectively.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RegisteredAnnotationBuilders {

    private RegisteredAnnotationBuilders() {
    }

    /**
     * Builds an annotation with the builder registered for its type: among the builders of Micronaut, then of the
     * class loader of the annotation type, then of the given class loader. An annotation of a library and its
     * builder can be in a class loader below the one of Micronaut, and the builder of an application in a class
     * loader below the one of the annotation.
     *
     * @param annotationType  The annotation type
     * @param annotationValue The annotation value
     * @param classLoader     The class loader of the application, if known
     * @param <A>             The annotation type
     * @return The annotation
     * @throws IllegalStateException If no builder is registered for the annotation type
     */
    public static <A extends Annotation> A build(Class<A> annotationType,
                                                 AnnotationValue<A> annotationValue,
                                                 @Nullable ClassLoader classLoader) {
        AnnotationBuilder<A> builder = AnnotationBuilder.find(annotationType)
            .or(() -> find(annotationType, annotationType.getClassLoader()))
            .or(() -> classLoader != annotationType.getClassLoader() ? find(annotationType, classLoader) : Optional.empty())
            .orElseThrow(() -> new IllegalStateException(
                "No AnnotationBuilder is registered for " + annotationType.getName() + ": register it with @RegisterAnnotations"));
        return builder.build(annotationValue, ConversionService.SHARED);
    }

    private static <A extends Annotation> Optional<AnnotationBuilder<A>> find(Class<A> annotationType, @Nullable ClassLoader classLoader) {
        return classLoader != null ? AnnotationBuilder.find(annotationType, classLoader) : Optional.empty();
    }
}
