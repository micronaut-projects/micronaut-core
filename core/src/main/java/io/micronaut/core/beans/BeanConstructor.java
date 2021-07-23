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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;
import io.micronaut.core.type.Argument;

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
    @NonNull Class<T> getDeclaringBeanType();

    /**
     * @return The constructor argument types.
     */
    @NonNull Argument<?>[] getArguments();

    /**
     * Instantiate an instance.
     * @param parameterValues The parameter values
     * @return The instance, never null.
     */
    @NonNull T instantiate(Object... parameterValues);

    /**
     * The description of the constructor.
     * @return The description
     */
    @Override
    @NonNull
    default String getDescription() {
        return getDescription(true);
    }

    /**
     * The description of the constructor.
     * @param simple Whether to return a simple representation without package names
     * @return The description
     */
    @Override
    @NonNull
    default String getDescription(boolean simple) {
        String args = Arrays.stream(getArguments())
                .map(arg -> arg.getTypeString(simple) + " " + arg.getName())
                .collect(Collectors.joining(","));
        return getDeclaringBeanType().getSimpleName() + "(" + args + ")";
    }
}
