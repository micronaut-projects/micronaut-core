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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Qualifier} that qualifies beans by generic type arguments.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class TypeArgumentQualifier<T> implements Qualifier<T> {

    private static final Logger LOG = ClassUtils.getLogger(TypeArgumentQualifier.class);
    private final Class[] typeArguments;

    /**
     * @param typeArguments The type arguments
     */
    TypeArgumentQualifier(Class... typeArguments) {
        this.typeArguments = typeArguments;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> beanType.isAssignableFrom(candidate.getBeanType()))
                .filter(candidate -> {

            List<Class> typeArguments = getTypeArguments(beanType, candidate);

            boolean result = areTypesCompatible(typeArguments);
            if (LOG.isTraceEnabled() && !result) {
                LOG.trace("Bean type {} is not compatible with candidate generic types [{}] of candidate {}", beanType, CollectionUtils.toString(typeArguments), candidate);
            }
            return result;
        });
    }

    /**
     * @return The type arguments
     */
    public Class[] getTypeArguments() {
        return typeArguments;
    }

    /**
     * @param classes An array of classes
     * @return Whether the types are compatible
     */
    protected boolean areTypesCompatible(List<Class> classes) {
        final Class[] typeArguments = this.typeArguments;
        return areTypesCompatible(typeArguments, classes);
    }

    /**
     * @param beanType   The bean type
     * @param candidate  The candidate
     * @param <BT>       The bean type subclass
     * @return The list of type arguments
     */
    protected <BT extends BeanType<T>> List<Class> getTypeArguments(Class<T> beanType, BT candidate) {
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

    /**
     * Are the given types compatible.
     *
     * @param typeArguments The type arguments
     * @param classes       The classes to check for alignments
     * @return True if they are
     */
    public static boolean areTypesCompatible(Class[] typeArguments, List<Class> classes) {
        if (classes.size() == 0) {
            // in this case the type doesn't specify type arguments, so this is the equivalent of using Object
            return true;
        } else {
            if (classes.size() != typeArguments.length) {
                return false;
            } else {
                for (int i = 0; i < classes.size(); i++) {
                    Class left = classes.get(i);
                    Class right = typeArguments[i];
                    if (right == Object.class) {
                        continue;
                    }
                    if (left != right && !left.isAssignableFrom(right)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypeArgumentQualifier<?> that = (TypeArgumentQualifier<?>) o;
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
