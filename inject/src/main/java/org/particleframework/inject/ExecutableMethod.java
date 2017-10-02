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
import org.particleframework.core.type.Executable;
import org.particleframework.core.type.ReturnType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * <p>An executable method is a compile time produced invocation of a method call. Avoiding the use of reflection and allowing the JIT to optimize the call</p>
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

    /**
     * <p>The annotated elements that this {@link ExecutableMethod} is able to resolve annotations from</p>
     *
     * <p>These elements are used when resolving annotations via the {@link #findAnnotationsWithStereoType(Class)} method</p>
     *
     * @return An array of {@link AnnotatedElement} instances
     */
    default AnnotatedElement[] getAnnotatedElements() {
        return AnnotationUtil.ZERO_ANNOTATED_ELEMENTS;
    }
}
