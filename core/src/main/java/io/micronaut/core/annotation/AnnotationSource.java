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
import java.util.*;

/**
 * <p>
 * An abstract interface around an source of {@link Annotation} instances. Since bean definition instances
 * can read annotations from many potential sources (factory beans, factory methods, method level, class level etc.). This
 * abstraction allows a simplified view for annotation discovery.
 * </p>
 * <p>
 * <p>Generally annotation sources are prioritized in the following order:</p>
 * <p>
 * <ol>
 *     <li>Method Level</li>
 *     <li>Class Level</li>
 *     <li>Factory Method</li>
 *     <li>Factory Class</li>
 * </ol>
 * <p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationSource extends AnnotatedElement {
    /**
     * An empty annotation source.
     */
    AnnotationSource EMPTY = () -> AnnotationUtil.ZERO_ANNOTATED_ELEMENTS;
    /**
     * <p>The annotated elements that this {@link AnnotationSource} is able to resolve annotations from</p>.
     *
     * @return An array of {@link AnnotatedElement} instances
     */
    AnnotatedElement[] getAnnotatedElements();


    /**
     * Get a concrete annotation for the given annotation type searching all of the {@link #getAnnotatedElements()}.
     *
     * @param annotationClass The annotation class
     * @param <T>             The annotation generic type
     * @return The annotation or null if it doesn't exist
     */
    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        AnnotationMetadata annotationMetadata = null;
        if (this instanceof AnnotationMetadataProvider) {
            annotationMetadata = ((AnnotationMetadataProvider) this).getAnnotationMetadata();
        } else if (this instanceof AnnotationMetadata) {
            annotationMetadata = (AnnotationMetadata) this;
        }

        if (annotationMetadata != null && annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
            return annotationMetadata.getAnnotation(annotationClass);
        } else {
            // slow path
            for (AnnotatedElement annotatedElement : getAnnotatedElements()) {
                try {
                    T annotation = annotatedElement.getAnnotation(annotationClass);
                    if (annotation != null) {
                        return annotation;
                    }
                } catch (ArrayStoreException | TypeNotPresentException e) {
                    // ignore, annotation that references a class not on the classpath
                }
            }
            return null;
        }

    }

    /**
     * @return A merged view of all of the annotations from {@link  #getAnnotatedElements()}
     */
    @Override
    default Annotation[] getAnnotations() {
        AnnotationMetadata annotationMetadata = null;
        if (this instanceof AnnotationMetadataProvider) {
            annotationMetadata = ((AnnotationMetadataProvider) this).getAnnotationMetadata();
        } else if (this instanceof AnnotationMetadata) {
            annotationMetadata = (AnnotationMetadata) this;
        }

        if (annotationMetadata != null && annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
            return annotationMetadata.getAnnotations();
        } else {
            AnnotatedElement[] elements = getAnnotatedElements();
            return Arrays.stream(elements)
                    .flatMap(element -> Arrays.stream(element.getAnnotations()))
                    .toArray(Annotation[]::new);
        }
    }

    /**
     * @return A merged view of all of the declared annotations from {@link  #getAnnotatedElements()}
     */
    @Override
    default Annotation[] getDeclaredAnnotations() {
        AnnotationMetadata annotationMetadata = null;
        if (this instanceof AnnotationMetadataProvider) {
            annotationMetadata = ((AnnotationMetadataProvider) this).getAnnotationMetadata();
        } else if (this instanceof AnnotationMetadata) {
            annotationMetadata = (AnnotationMetadata) this;
        }

        if (annotationMetadata != null && annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
            return annotationMetadata.getDeclaredAnnotations();
        } else {
            AnnotatedElement[] elements = getAnnotatedElements();
            return Arrays.stream(elements)
                    .flatMap(element -> Arrays.stream(element.getDeclaredAnnotations()))
                    .toArray(Annotation[]::new);
        }
    }

}
