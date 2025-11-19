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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;

import java.util.Objects;
import java.util.Optional;

/**
 * Qualifies using a name.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class NameQualifier<T> extends FilteringQualifier<T> implements io.micronaut.core.naming.Named {

    private final String name;

    NameQualifier(@NonNull String name) {
        this.name = Objects.requireNonNull(name, "Argument [name] cannot be null");
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (QualifierUtils.match(candidate, this)) {
            return true;
        }
        return match(beanType, candidate);
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        if (QualifierUtils.matchQualified(candidate, this)) {
            return true;
        }
        return match(beanType, candidate);
    }

    private boolean match(Class<T> beanType, BeanType<T> candidate) {
        // here we resolved the declared Qualifier of the bean
        String thisName = candidate.getAnnotationMetadata()
            .findDeclaredAnnotation(AnnotationUtil.NAMED)
            .flatMap(AnnotationValue::stringValue)
            .orElse(null);
        if (thisName == null) {
            if (candidate instanceof NameResolver) {
                Optional<String> resolvedName = ((NameResolver) candidate).resolveName();
                thisName = resolvedName.orElse(candidate.getBeanType().getSimpleName());
            } else {
                thisName = candidate.getBeanType().getSimpleName();
            }
        }
        return thisName.equalsIgnoreCase(name) || thisName.equalsIgnoreCase(name + beanType.getSimpleName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !NameQualifier.class.isAssignableFrom(o.getClass())) {
            return false;
        }

        NameQualifier<?> that = (NameQualifier<?>) o;

        return name.equals(that.name);
    }

    @Override
    public String toString() {
        return "@Named('" + name + "')";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String getName() {
        return name;
    }

}
