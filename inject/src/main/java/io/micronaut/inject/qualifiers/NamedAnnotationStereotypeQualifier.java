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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Qualifiers for a named stereotype.
 * @param <T> The generic type
 * @since 3.0.0
 * @author graemerocher
 */
@Internal
final class NamedAnnotationStereotypeQualifier<T> implements Qualifier<T> {

    final String stereotype;

    /**
     * @param stereotype The stereotype
     */
    NamedAnnotationStereotypeQualifier(String stereotype) {
        this.stereotype = Objects.requireNonNull(stereotype, "Stereotype cannot be null");
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> candidate.getAnnotationMetadata().hasStereotype(stereotype));
    }

    @Override
    public String toString() {
        return "@" + stereotype;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof NamedAnnotationStereotypeQualifier) {
            NamedAnnotationStereotypeQualifier<?> that = (NamedAnnotationStereotypeQualifier<?>) o;
            return Objects.equals(stereotype, that.stereotype);
        } else if (o instanceof AnnotationStereotypeQualifier) {
            AnnotationStereotypeQualifier<?> that = (AnnotationStereotypeQualifier<?>) o;
            return Objects.equals(stereotype, that.stereotype.getName());
        } else if (o instanceof AnnotationMetadataQualifier) {
            AnnotationMetadataQualifier<?> that = (AnnotationMetadataQualifier<?>) o;
            if (that.qualifierAnn == null) {
                return Objects.equals(stereotype, that.qualifiedName);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stereotype);
    }
}
