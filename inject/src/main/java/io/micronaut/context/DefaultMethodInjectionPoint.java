/*
 * Copyright 2018 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.MethodInjectionPoint;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A method injection point that does not use reflection
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class DefaultMethodInjectionPoint extends AbstractExecutable implements MethodInjectionPoint {

    private final BeanDefinition declaringBean;
    private final boolean requiresReflection;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Constructs a new {@link DefaultMethodInjectionPoint}
     * @param declaringBean The declaring bean
     * @param declaringType The declaring type
     * @param methodName The method name
     * @param arguments The arguments
     * @param requiresReflection Whether reflection is required
     */
    DefaultMethodInjectionPoint(
            BeanDefinition declaringBean,
            Class<?> declaringType,
            String methodName,
            @Nullable Argument[] arguments,
            boolean requiresReflection) {
       this(declaringBean, declaringType, methodName, arguments, AnnotationMetadata.EMPTY_METADATA, requiresReflection);
    }

    /**
     * Constructs a new {@link DefaultMethodInjectionPoint}
     * @param declaringBean The declaring bean
     * @param declaringType The declaring type
     * @param methodName The method name
     * @param arguments The arguments
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether reflection is required
     */
    DefaultMethodInjectionPoint(
            BeanDefinition declaringBean,
            Class<?> declaringType,
            String methodName,
            @Nullable Argument[] arguments,
            @Nullable AnnotationMetadata annotationMetadata,
            boolean requiresReflection) {
        super(declaringType, methodName, arguments);
        Objects.requireNonNull(declaringBean, "Declaring bean cannot be null");
        this.declaringBean = declaringBean;
        this.requiresReflection = requiresReflection;
        this.annotationMetadata = annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public Method getMethod() {
        return getTargetMethod();
    }

    @Override
    public String getName() {
        return methodName;
    }

    @Override
    public boolean isPreDestroyMethod() {
        return annotationMetadata.hasDeclaredAnnotation(PreDestroy.class);
    }

    @Override
    public boolean isPostConstructMethod() {
        return annotationMetadata.hasDeclaredAnnotation(PostConstruct.class);
    }

    @Override
    public Object invoke(Object instance, Object... args) {
        Method targetMethod = getTargetMethod();
        return ReflectionUtils.invokeMethod(instance, targetMethod, args);
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        // TODO: replace with annotation metadata
        return new AnnotatedElement[] {
                getTargetMethod(),
                declaringBean
        };
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return declaringBean;
    }

    @Override
    public boolean requiresReflection() {
        return requiresReflection;
    }
}
