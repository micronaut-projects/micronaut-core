/*
 * Copyright 2017 original authors
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
package org.particleframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * <p>
 * An abstract interface around an source of {@link Annotation} instances. Since bean definition instances
 * can read annotations from many potential sources (factory beans, factory methods, method level, class level etc.). This
 * abstraction allows a simplified view for annotation discovery.
 * </p>
 *
 * <p>Generally annotation sources are prioritized in the following order:</p>
 *
 * <ol>
 *     <li>Method Level</li>
 *     <li>Class Level</li>
 *     <li>Factory Method</li>
 *     <li>Factory Class</li>
 * </ol>
 *
 * <p>The {@link #findAnnotation(Class)} method will return this first discovered annotation whilst traversing the above list.</p>
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationSource extends AnnotatedElement {


    /**
     * <p>The annotated elements that this {@link AnnotationSource} is able to resolve annotations from</p>
     *
     * <p>These elements are used when resolving annotations via the {@link #findAnnotationsWithStereoType(Class)} method</p>
     *
     * @return An array of {@link AnnotatedElement} instances
     */
    AnnotatedElement[] getAnnotatedElements();


    /**
     * Find an annotation by type from the {@link #getAnnotatedElements()} of this class
     *
     * @param type The type
     * @param <A> The generic type
     * @return An {@link Optional} of the type
     */
    default <A extends Annotation> Optional<A> findAnnotation(Class<A> type) {
        AnnotatedElement[] elements = getAnnotatedElements();
        for (AnnotatedElement element : elements) {
            Optional<? extends Annotation> result = AnnotationUtil.findAnnotationsWithStereoType(element, type)
                    .stream()
                    .filter(ann-> ann.annotationType() == type)
                    .findFirst();
            if(result.isPresent()) {
                return (Optional<A>) result;
            }
        }
        return Optional.empty();
    }

    /**
     * Find the first annotation for the given stereotype on the method
     *
     * @param stereotype The method
     * @return The stereotype
     */
    default Optional<Annotation> findAnnotationWithStereoType(Class<?> stereotype) {
        AnnotatedElement[] candidates = getAnnotatedElements();
        Set<Annotation> results = AnnotationUtil.findAnnotationsWithStereoType(candidates, stereotype);
        return results.stream().findFirst();
    }

    /**
     * Find all the annotations for the given stereotype on the method
     * @param stereotype The method
     * @return The stereotype
     */
    default Set<Annotation> findAnnotationsWithStereoType(Class<?> stereotype) {
        AnnotatedElement[] candidates = getAnnotatedElements();
        return AnnotationUtil.findAnnotationsWithStereoType(candidates, stereotype);
    }


    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        for (AnnotatedElement annotatedElement : getAnnotatedElements()) {
            T annotation = annotatedElement.getAnnotation(annotationClass);
            if(annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    @Override
    default Annotation[] getAnnotations() {
        AnnotatedElement[] elements = getAnnotatedElements();
        ArrayList<Annotation> annotations = new ArrayList<>();
        for (AnnotatedElement element : elements) {
            Collections.addAll(annotations, element.getAnnotations());
        }
        return annotations.toArray(new Annotation[annotations.size()]);
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
        AnnotatedElement[] elements = getAnnotatedElements();
        ArrayList<Annotation> annotations = new ArrayList<>();
        for (AnnotatedElement element : elements) {
            Collections.addAll(annotations, element.getDeclaredAnnotations());
        }
        return annotations.toArray(new Annotation[annotations.size()]);
    }

}
