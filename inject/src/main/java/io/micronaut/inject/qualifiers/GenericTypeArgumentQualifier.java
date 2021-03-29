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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class GenericTypeArgumentQualifier<T> implements Qualifier<T> {
    private final Class<?>[] typeArguments;

    /**
     * @param typeArguments The type arguments
     */
    GenericTypeArgumentQualifier(Class<?>... typeArguments) {
        this.typeArguments = typeArguments;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> beanType.isAssignableFrom(candidate.getBeanType()))
                .filter(candidate -> {

                    if (candidate instanceof BeanDefinition) {
                        final BeanDefinition<?> beanDefinition = (BeanDefinition<?>) candidate;
                        if (beanDefinition.hasDeclaredAnnotation(Any.class)) {
                            return true;
                        }
                        final Class<?>[] beanTypeParameters = beanDefinition.getTypeParameters(beanType);
                        if (typeArguments.length != beanTypeParameters.length) {
                            return false;
                        }

                        for (int i = 0; i < beanTypeParameters.length; i++) {
                            Class<?> candidateParameter = beanTypeParameters[i];
                            final Class<?> requestedParameter = typeArguments[i];

                            if (!requestedParameter.isAssignableFrom(candidateParameter)) {
                                return false;
                            }

                        }
                        return true;
                    }
                    return false;
                });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GenericTypeArgumentQualifier<?> that = (GenericTypeArgumentQualifier<?>) o;
        return Arrays.equals(typeArguments, that.typeArguments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(typeArguments);
    }

    @Override
    public String toString() {
        return "<" + Arrays.stream(typeArguments).map(Class::getSimpleName).collect(Collectors.joining(",")) + ">";
    }
}
