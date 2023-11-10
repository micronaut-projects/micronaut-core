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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;

/**
 * Models a return type of {@link Executable} method in Micronaut.
 *
 * @param <T> The concrete type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ReturnType<T> extends TypeInformation<T>, AnnotationMetadataProvider, ArgumentCoercible<T> {

    /**
     * @return The return type as an argument
     */
    @Override
    default @NonNull Argument<T> asArgument() {
        Collection<Argument<?>> values = getTypeVariables().values();
        return Argument.of(getType(), values.toArray(Argument.ZERO_ARGUMENTS));
    }

    /**
     * @return Is the return type suspended function (Kotlin).
     * @since 2.0.0
     */
    default boolean isSuspended() {
        return false;
    }

    /**
     * @return Is the return type a single result or multiple results
     * @since 2.0
     */
    default boolean isSingleResult() {
        if (isSpecifiedSingle()) {
            return true;
        } else {
            if (isReactive()) {
                Class<T> returnType = getType();
                return RuntimeTypeInformation.isSingle(returnType);
            } else {
                return true;
            }
        }
    }

    /**
     * Create a new return type from the given type and arguments.
     *
     * @param type          The type
     * @param typeArguments The type arguments
     * @param <T1>          The return type
     * @return A {@link ReturnType}
     */
    static <T1> ReturnType<T1> of(Class<T1> type, Argument<?>... typeArguments) {
        Map<String, Argument<?>> argumentMap = CollectionUtils.newLinkedHashMap(typeArguments.length);
        for (Argument<?> argument : typeArguments) {
            argumentMap.put(argument.getName(), argument);
        }
        return new ReturnType<T1>() {
            @Override
            public Class<T1> getType() {
                return type;
            }

            @Override
            public Argument[] getTypeParameters() {
                return typeArguments;
            }

            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return argumentMap;
            }
        };
    }
}
