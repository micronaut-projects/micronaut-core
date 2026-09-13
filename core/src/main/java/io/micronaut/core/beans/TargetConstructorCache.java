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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

/**
 * Holds the {@link Constructor} a {@link BeanConstructor} resolved, so that it is resolved once, whether or not
 * there is one. A {@code null} result is held the same way as a found constructor: the bean type may not declare
 * a constructor with the arguments, or the bean may not be created through a constructor at all.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.2
 */
@Internal
public final class TargetConstructorCache<T> {

    private @Nullable Constructor<T> constructor;
    private boolean resolved;

    /**
     * Returns the held constructor, resolving it on the first call.
     *
     * @param resolver The resolution, run once
     * @return The constructor, or {@code null} if the resolution produced none
     */
    public @Nullable Constructor<T> get(Supplier<@Nullable Constructor<T>> resolver) {
        if (!resolved) {
            constructor = resolver.get();
            resolved = true;
        }
        return constructor;
    }

    /**
     * Resolves the declared constructor of the bean type whose parameter types are the raw types of the bean
     * constructor's arguments, the resolution {@link BeanConstructor#getTargetConstructor()} performs by default.
     *
     * @param beanConstructor The bean constructor
     * @param <T>             The bean type
     * @return The constructor, or {@code null} if the bean type declares no such constructor
     */
    public static <T> @Nullable Constructor<T> resolve(BeanConstructor<T> beanConstructor) {
        Argument<?>[] arguments = beanConstructor.getArguments();
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            parameterTypes[i] = arguments[i].getType();
        }
        return ReflectionUtils.findConstructor(beanConstructor.getDeclaringBeanType(), parameterTypes).orElse(null);
    }
}
