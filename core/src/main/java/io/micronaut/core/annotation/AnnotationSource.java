/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Subclass of {@link AnnotatedElement} that provides default implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationSource extends AnnotatedElement {
    /**
     * An empty annotation source.
     */
    AnnotationSource EMPTY = new AnnotationSource() { };

    /**
     * Returns null by default.
     *
     * @see AnnotatedElement#getAnnotation(Class)
     */
    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    /**
     * @see AnnotatedElement#getAnnotations()
     */
    @Override
    default Annotation[] getAnnotations() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    /**
     * @see AnnotatedElement#getDeclaredAnnotations()
     */
    @Override
    default Annotation[] getDeclaredAnnotations() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    @Override
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    /**
     * Variation of {@link #isAnnotationPresent(Class)} for declared annotations
     * @param annotationClass The annotation class
     * @return True if it is
     */
    default boolean isDeclaredAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }
}
