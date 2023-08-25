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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.context.Qualifier} that qualifies beans by generic type arguments and only
 * returns the candidates that most closely match.
 *
 * @param <T> The type
 * @author James Kleeh
 * @since 1.1.1
 */
@Internal
public final class ClosestTypeArgumentQualifier<T> implements Qualifier<T> {

    private static final Logger LOG = ClassUtils.getLogger(ClosestTypeArgumentQualifier.class);
    private final List<Class<?>>[] hierarchies;
    private final Class<?>[] typeArguments;

    /**
     * @param typeArguments The type arguments
     */
    ClosestTypeArgumentQualifier(Class<?>... typeArguments) {
        this.typeArguments = typeArguments;
        this.hierarchies = new List[typeArguments.length];
        for (int i = 0 ; i < typeArguments.length; i++) {
            hierarchies[i] = ClassUtils.resolveHierarchy(typeArguments[i]);
        }
    }

    /**
     * @return The type arguments
     */
    public Class<?>[] getTypeArguments() {
        return typeArguments;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates
                .filter(candidate -> beanType.isAssignableFrom(candidate.getBeanType()))
                .map(candidate -> {
                    List<Class<?>> typeArguments = getTypeArguments(beanType, candidate);

                    int result = compare(typeArguments);
                    if (LOG.isTraceEnabled() && result < 0) {
                        LOG.trace("Bean type {} is not compatible with candidate generic types [{}] of candidate {}", beanType, CollectionUtils.toString(typeArguments), candidate);
                    }
                    return new AbstractMap.SimpleEntry<>(candidate, result);
                })
                .filter(entry -> entry.getValue() > -1)
                .collect(StreamUtils.minAll(
                        Comparator.comparingInt(Map.Entry::getValue),
                        Collectors.toList())
                )
                .stream()
                .map(Map.Entry::getKey);
    }

    /**
     * @param classesToCompare An array of classes
     * @return Whether the types are compatible
     */
    private int compare(List<Class<?>> classesToCompare) {
        if (classesToCompare.isEmpty() && typeArguments.length == 0) {
            return 0;
        } else if (classesToCompare.size() != typeArguments.length) {
            return -1;
        } else {
            int comparison = 0;
            for (int i = 0; i < classesToCompare.size(); i++) {
                if (typeArguments[i] == Object.class) {
                    continue;
                }
                Class<?> left = classesToCompare.get(i);
                List<Class<?>> hierarchy =  hierarchies[i];
                int index = hierarchy.indexOf(left);
                if (index == -1) {
                    comparison = -1;
                    break;
                }
                comparison = comparison + index;
            }
            return comparison;
        }
    }

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
}
