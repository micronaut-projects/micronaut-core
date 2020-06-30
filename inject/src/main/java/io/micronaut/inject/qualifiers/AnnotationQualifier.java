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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.stream.Stream;

/**
 * Qualifies using an annotation.
 *
 * @param <T> Type type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationQualifier<T> extends NameQualifier<T> {

    private final Annotation qualifier;

    /**
     * @param qualifier The qualifier
     */
    AnnotationQualifier(Annotation qualifier) {
        super(qualifier.annotationType().getSimpleName());
        this.qualifier = qualifier;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        String name;
        if (qualifier instanceof Named) {
            Named named = (Named) qualifier;
            String v = named.value();
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);

        } else {
            name = qualifier.annotationType().getSimpleName();
        }

        return reduceByAnnotation(beanType, candidates, name, qualifier.annotationType().getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnnotationQualifier<?> that = (AnnotationQualifier<?>) o;

        return qualifier.equals(that.qualifier);
    }

    @Override
    public int hashCode() {
        return qualifier.hashCode();
    }

    @Override
    public String toString() {
        return '@' + qualifier.annotationType().getSimpleName();
    }
}
