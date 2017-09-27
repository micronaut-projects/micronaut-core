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
package org.particleframework.inject;

import org.particleframework.core.annotation.AnnotationUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * <p>An invocable method is a compile time produced invocation of a method call. Avoiding the use of reflection and allowing the JIT to optimize the call</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 */
public interface ExecutableMethod<T, R> extends Executable<T,R> {

    /**
     * @return The target method
     */
    Method getTargetMethod();
    /**
     * @return Return the return type
     */
    ReturnType<R> getReturnType();
    /**
     * @return The bean that declares this injection point
     */
    Class<?> getDeclaringType();

    /**
     * @return The name of the method
     */
    String getMethodName();

    /**
     * @return The argument types
     */
    Class[] getArgumentTypes();

    /**
     * @return One or many {@link org.particleframework.inject.annotation.Executable} annotations for this method
     */
    Set<? extends Annotation> getExecutableAnnotations();

    @Override
    default <A extends Annotation> Optional<A> findAnnotation(Class type) {
        AnnotatedElement[] elements = getAnnotatedElements();
        for (AnnotatedElement element : elements) {
            Optional<? extends Annotation> result = AnnotationUtil.findAnnotationsWithStereoType(element, type)
                    .stream()
                    .findFirst();
            if(result.isPresent()) {
                return (Optional<A>) result;
            }
        }
        return Optional.empty();
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

    /**
     * <p>The annotated elements that this {@link ExecutableMethod} is able to resolve annotations from</p>
     *
     * <p>These elements are used when resolving annotations via the {@link #findAnnotationsWithStereoType(Class)} method</p>
     *
     * @return An array of {@link AnnotatedElement} instances
     */
    default AnnotatedElement[] getAnnotatedElements() {
        return resolveAnnotationElements(this);
    }

    /**
     * Resolves {@link AnnotatedElement} instances to use for the method and the given sources
     *
     * @param method The method
     * @param sources The sources
     * @return An array of elements
     */
    static AnnotatedElement[] resolveAnnotationElements(ExecutableMethod method, AnnotatedElement...sources) {
        if(sources.length == 0) {
            return new AnnotatedElement[] { method.getTargetMethod(), method.getDeclaringType()};
        }
        else {
            AnnotatedElement[] elements = new AnnotatedElement[sources.length+2];
            elements[0] = method.getTargetMethod();
            elements[1] = method.getDeclaringType();
            System.arraycopy(sources, 0, elements, 2, sources.length);
            return elements;
        }
    }
}
