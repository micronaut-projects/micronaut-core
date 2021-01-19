/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.AbstractBeanMethod;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Subclass of {@link AbstractBeanMethod} that also implements {@link ExecutableMethod}.
 * @param <B> The bean type
 * @param <T> The return type
 * @since 2.3.0
 * @author graemerocher
 */
@Internal
@UsedByGeneratedCode
public abstract class AbstractExecutableBeanMethod<B, T> extends AbstractBeanMethod<B, T> implements ExecutableMethod<B, T> {
    /**
     * Default constructor.
     *
     * @param introspection      The associated introspection
     * @param returnType         The return type
     * @param name               The name of the method
     * @param annotationMetadata The annotation metadata
     * @param arguments          The argument types
     */
    protected AbstractExecutableBeanMethod(
            @NotNull BeanIntrospection<B> introspection,
            @NotNull Argument<T> returnType,
            @NotNull String name,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument<?>... arguments) {
        super(introspection, returnType, name, annotationMetadata, arguments);
    }

    @Override
    public Method getTargetMethod() {
        if (ClassUtils.REFLECTION_LOGGER.isWarnEnabled()) {
            ClassUtils.REFLECTION_LOGGER.warn("Using getTargetMethod for method {} on type {} requires the use of reflection. GraalVM configuration necessary", getName(), getDeclaringType());
        }
        return ReflectionUtils.getRequiredMethod(getDeclaringType(), getMethodName(), getArgumentTypes());
    }

    @Override
    public Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }

    @Override
    public String getMethodName() {
        return getName();
    }
}
