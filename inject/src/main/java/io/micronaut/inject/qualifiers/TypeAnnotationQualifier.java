/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Type;
import io.micronaut.inject.BeanType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link Type} qualifier.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
class TypeAnnotationQualifier<T> implements Qualifier<T> {

    private final List<Class> types;

    /**
     * @param types The types
     */
    TypeAnnotationQualifier(@Nullable Class<?>... types) {
        this.types = new ArrayList<>();
        if (types != null) {
            for (Class<?> type : types) {
                Type typeAnn = type.getAnnotation(Type.class);
                if (typeAnn != null) {
                    this.types.addAll(Arrays.asList(typeAnn.value()));
                } else {
                    this.types.add(type);
                }
            }
        }
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> areTypesCompatible(candidate.getBeanType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypeAnnotationQualifier<?> that = (TypeAnnotationQualifier<?>) o;

        return types.equals(that.types);
    }

    @Override
    public int hashCode() {
        return types.hashCode();
    }

    @Override
    public String toString() {
        return "<" + types.stream().map(Class::getSimpleName).collect(Collectors.joining("|")) + ">";
    }

    /**
     * @param type The type
     * @return Whether the types are compatible
     */
    protected boolean areTypesCompatible(Class type) {
        return types.stream().anyMatch(c ->
            c.isAssignableFrom(type)
        );
    }
}

