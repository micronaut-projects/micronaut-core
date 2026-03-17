/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A {@link Qualifier} composed of other qualifiers.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class FilteringCompositeQualifier<T> extends FilteringQualifier<T> {

    private final FilteringQualifier<T>[] qualifiers;

    /**
     * @param qualifiers The qualifiers
     */
    FilteringCompositeQualifier(FilteringQualifier<T>[] qualifiers) {
        this.qualifiers = qualifiers;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        for (FilteringQualifier<T> qualifier : qualifiers) {
            if (!qualifier.doesQualify(beanType, candidate)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        for (FilteringQualifier<T> qualifier : qualifiers) {
            if (!qualifier.doesQualify(beanType, candidate)) {
                return false;
            }
        }
        return true;
    }

    public FilteringQualifier<T>[] getQualifiers() {
        return qualifiers;
    }

    @Override
    public boolean contains(Qualifier<T> qualifier) {
        if (qualifier instanceof FilteringCompositeQualifier<T> filteringCompositeQualifier) {
            for (Qualifier<T> q : filteringCompositeQualifier.qualifiers) {
                if (!contains(q)) {
                    return false;
                }
            }
            return true;
        }
        if (qualifier instanceof CompositeQualifier<T> compositeQualifier) {
            for (Qualifier<T> q : compositeQualifier.getQualifiers()) {
                if (!contains(q)) {
                    return false;
                }
            }
            return true;
        }
        for (FilteringQualifier<T> q : qualifiers) {
            if (q.contains(qualifier)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilteringCompositeQualifier<?> that = (FilteringCompositeQualifier<?>) o;
        return Arrays.equals(qualifiers, that.qualifiers);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(qualifiers);
    }

    @Override
    public String toString() {
        return Arrays.stream(qualifiers).map(Object::toString).collect(Collectors.joining(" and "));
    }
}
