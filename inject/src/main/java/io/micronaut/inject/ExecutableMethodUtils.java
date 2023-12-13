/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.Annotation;

/**
 * Utils class to verify if an {@link io.micronaut.context.annotation.Executable} method in a bean has an annotation.
 * @author Sergio del Amo
 * @since 4.3.0
 */
@Internal
public final class ExecutableMethodUtils {
    private ExecutableMethodUtils() {
    }

    /**
     *
     * @param beanContext Bean Context
     * @param bean Bean
     * @param annotationClass Annotation Class
     * @param methodName The method name
     * @param argumentTypes The argument types
     * @return Whether a method is annotated with the supplied annotation.
     */
    public static boolean hasAnnotationInMethod(@NonNull BeanContext beanContext,
                                                @NonNull Object bean,
                                                @NonNull Class<? extends Annotation> annotationClass,
                                                @NonNull String methodName,
                                                Class<?>... argumentTypes) {
        return beanContext.findBeanRegistration(bean)
                .map(beanRegistration -> hasAnnotationInMethod(beanRegistration, annotationClass, methodName, argumentTypes))
                .orElse(false);
    }

    /**
     *
     * @param beanRegistration Bean Registration
     * @param annotationClass Annotation Class
     * @param methodName The method name
     * @param argumentTypes The argument types
     * @return Whether a method is annotated with the supplied annotation.
     */
    public static boolean hasAnnotationInMethod(@NonNull BeanRegistration<?> beanRegistration,
                                                @NonNull Class<? extends Annotation> annotationClass,
                                                @NonNull String methodName,
                                                Class<?>... argumentTypes) {
        return hasAnnotationInMethod(beanRegistration.getBeanDefinition(), annotationClass, methodName, argumentTypes);
    }

    /**
     *
     * @param beanDefinition Bean Definition
     * @param annotationClass Annotation Class
     * @param methodName The method name
     * @param argumentTypes The argument types
     * @return Whether a method is annotated with the supplied annotation.
     */
    public static boolean hasAnnotationInMethod(@NonNull BeanDefinition<?> beanDefinition,
                                                @NonNull Class<? extends Annotation> annotationClass,
                                                @NonNull String methodName,
                                                Class<?>... argumentTypes) {
        return  beanDefinition.findMethod(methodName, argumentTypes)
                .filter(executableMethod -> isMethodAnnotatedWith(executableMethod, annotationClass))
                .isPresent();
    }


    /**
     *
     * @param executableMethod Executable Method
     * @param annotationClass Annotation Class
     * @return Whether the method is annotated with the supplied annotation.
     */
    private static boolean isMethodAnnotatedWith(@NonNull ExecutableMethod<?, ?> executableMethod,
                                                 @NonNull Class<? extends Annotation> annotationClass) {
        return executableMethod.getAnnotationMetadata().hasAnnotation(annotationClass);
    }
}
