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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Qualifies by the exact type argument name. Useful for qualifying when inheritance is not a factory such as by annotation.
 *
 * @param <T> The generic type name
 * @since 3.0.0
 * @author graemerocher
 */
final class ExactTypeArgumentNameQualifier<T> implements Qualifier<T> {
    private static final Logger LOG = ClassUtils.getLogger(TypeArgumentQualifier.class);
    private final String typeName;

    ExactTypeArgumentNameQualifier(String typeName) {
        this.typeName = Objects.requireNonNull(typeName, "Type name cannot be null");
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> beanType.isAssignableFrom(candidate.getBeanType()))
                .filter(candidate -> {
                    final List<Class<?>> typeArguments = getTypeArguments(beanType, candidate);
                    boolean result = areTypesCompatible(typeArguments);
                    if (LOG.isTraceEnabled() && !result) {
                        LOG.trace("Bean type {} is not compatible with candidate generic types [{}] of candidate {}", beanType, CollectionUtils.toString(typeArguments), candidate);
                    }

                    return result;
                });
    }

    private boolean areTypesCompatible(List<Class<?>> typeArguments) {
        if (typeArguments.isEmpty()) {
            return true;
        } else if (typeArguments.size() == 1) {
            for (Class<?> typeArgument : typeArguments) {
                if (typeName.equals(typeArgument.getTypeName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <BT extends BeanType<T>> List<Class<?>> getTypeArguments(Class<T> beanType, BT candidate) {
        if (candidate instanceof BeanDefinition) {
            BeanDefinition<BT> definition = (BeanDefinition<BT>) candidate;
            return definition.getTypeArguments(beanType).stream().map(Argument::getType).collect(Collectors.toList());
        } else {
            if (beanType.isInterface()) {
                return Arrays.asList(GenericTypeUtils.resolveInterfaceTypeArguments(candidate.getBeanType(), beanType));
            } else {
                return Arrays.asList(GenericTypeUtils.resolveSuperTypeGenericArguments(candidate.getBeanType(), beanType));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExactTypeArgumentNameQualifier<?> that = (ExactTypeArgumentNameQualifier<?>) o;
        return generify(typeName).equals(generify(that.typeName));
    }

    private String generify(String typeName) {
        return "<" + typeName + ">";
    }

    @Override
    public int hashCode() {
        return Objects.hash(generify(typeName));
    }

    @Override
    public String toString() {
        return generify(typeName);
    }
}
