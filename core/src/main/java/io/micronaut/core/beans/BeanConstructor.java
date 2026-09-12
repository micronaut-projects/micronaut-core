/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.Described;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Models a bean constructor.
 *
 * @param <T> The bean type
 * @since 3.0.0
 * @author graemerocher
 */
public interface BeanConstructor<T> extends AnnotationMetadataProvider, Described {
    /**
     * Returns the bean type.
     *
     * @return The underlying bean type
     */
    Class<T> getDeclaringBeanType();

    /**
     * @return The constructor argument types.
     */
    Argument<?>[] getArguments();

    /**
     * Instantiate an instance.
     * @param parameterValues The parameter values
     * @return The instance, never null.
     */
    T instantiate(@Nullable Object... parameterValues);

    /**
     * Returns the {@link Constructor} this bean constructor stands for: the constructor of
     * {@link #getDeclaringBeanType()} whose parameter types are the raw types of {@link #getArguments()}.
     *
     * <p>This is the counterpart of {@code io.micronaut.inject.MethodReference#getTargetMethod()} for
     * constructors. Framework implementations resolve the constructor once and hold it; this default
     * resolves it on every call, so an implementation that knows its constructor should return it directly.</p>
     *
     * <p>The result is {@code null} when the bean is not created through a constructor of the declaring
     * bean type: a bean produced by a factory method or field, or an introspection instantiating through a
     * static creator method whose parameter types no constructor shares.</p>
     *
     * @return The constructor, or {@code null} if the declaring bean type declares no such constructor
     * @since 5.2.2
     */
    @Nullable
    default Constructor<T> getTargetConstructor() {
        Argument<?>[] arguments = getArguments();
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            parameterTypes[i] = arguments[i].getType();
        }
        return ReflectionUtils.findConstructor(getDeclaringBeanType(), parameterTypes).orElse(null);
    }

    /**
     * The description of the constructor.
     * @return The description
     */
    @Override
    default String getDescription() {
        return getDescription(true);
    }

    /**
     * The description of the constructor.
     * @param simple Whether to return a simple representation without package names
     * @return The description
     */
    @Override
    default String getDescription(boolean simple) {
        String args = Arrays.stream(getArguments())
                .map(arg -> arg.getTypeString(simple) + " " + arg.getName())
                .collect(Collectors.joining(","));
        return getDeclaringBeanType().getSimpleName() + "(" + args + ")";
    }
}
