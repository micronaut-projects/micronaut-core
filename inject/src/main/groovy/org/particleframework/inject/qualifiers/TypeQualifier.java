/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.context.annotation.Type;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Nullable;
import org.particleframework.inject.BeanDefinition;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link org.particleframework.context.annotation.Type} qualifier
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class TypeQualifier<T> implements Qualifier<T> {

    private final List<Class> types;

    TypeQualifier(@Nullable Class... types) {
        this.types = new ArrayList<>();
        if(types != null) {
            for (Class type : types) {
                Type typeAnn = AnnotationUtil.findAnnotationWithStereoType(type, Type.class);
                if(typeAnn != null) {
                    this.types.addAll(Arrays.asList(typeAnn.value()));
                }
                else {
                    this.types.add(type);
                }

            }
        }
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        return candidates.filter(candidate -> areTypesCompatible(candidate.getType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeQualifier<?> that = (TypeQualifier<?>) o;

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

    protected boolean areTypesCompatible(Class type) {
        return types.stream().anyMatch(c ->
                c.isAssignableFrom(type)
        );
    }
}

