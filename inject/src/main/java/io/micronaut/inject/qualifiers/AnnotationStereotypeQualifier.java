/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link Qualifier} that qualifies based on a bean stereotype.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationStereotypeQualifier<T> implements Qualifier<T> {

    private final Class<? extends Annotation> stereotype;

    /**
     * @param stereotype The stereotype
     */
    AnnotationStereotypeQualifier(Class<? extends Annotation> stereotype) {
        this.stereotype = stereotype;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> candidate.getAnnotationMetadata().hasStereotype(stereotype));
    }

    @Override
    public String toString() {
        return "@" + stereotype.getSimpleName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationStereotypeQualifier<?> that = (AnnotationStereotypeQualifier<?>) o;
        return Objects.equals(stereotype, that.stereotype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stereotype);
    }
}
