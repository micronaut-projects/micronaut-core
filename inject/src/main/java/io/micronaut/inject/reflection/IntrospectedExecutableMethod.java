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
package io.micronaut.inject.reflection;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * An {@link io.micronaut.inject.ExecutableMethod} over the {@link BeanMethod} of a bean introspection, for a
 * specification API that names a method by its {@link Method}: the arguments and the metadata are the ones
 * the introspection carries, generated or reflective, and the method itself is the one the caller gave.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class IntrospectedExecutableMethod<T, R> extends AbstractExecutableMethod<T, R> {

    private final BeanMethod<T, R> beanMethod;
    private final Method method;

    /**
     * @param declaringType The type declaring the method
     * @param beanMethod    The method of the introspection of that type
     * @param method        The method the caller named
     */
    public IntrospectedExecutableMethod(Class<T> declaringType, BeanMethod<T, R> beanMethod, Method method) {
        super(declaringType, beanMethod.getName(), beanMethod.getReturnType().asArgument(), beanMethod.getArguments());
        this.beanMethod = beanMethod;
        this.method = method;
    }

    @Override
    @SuppressWarnings("NullAway") // a method can return null
    protected R invokeInternal(T instance, @Nullable Object[] arguments) {
        return beanMethod.invoke(instance, arguments);
    }

    @Override
    protected Method resolveTargetMethod() {
        return method;
    }

    @Override
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return beanMethod.getAnnotationMetadata();
    }
}
