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

import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.BeanType;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Qualifier utils.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class QualifierUtils {

    private QualifierUtils() {
    }

    /**
     * Check if provided bean type matches the candidate type.
     *
     * @param beanType  The bean type
     * @param candidate The candidate type
     * @param <T>       The type
     * @return true if matches
     */
    static <T> boolean matchType(Class<T> beanType, BeanType<T> candidate) {
        return candidate.isContainerType() || beanType.isAssignableFrom(beanType);
    }

    /**
     * Check if the candidate is of qualifier {@link Any}.
     *
     * @param beanType  The bean type
     * @param candidate The candidate type
     * @param <T>       The type
     * @return true if matches
     */
    static <T> boolean matchAny(Class<T> beanType, BeanType<T> candidate) {
        return beanType != Object.class && candidate.getAnnotationMetadata().hasDeclaredAnnotation(Any.class);
    }

    /**
     * Check if matched name is matching with defined name of the bean.
     *
     * @param <T>       The type
     * @param candidate The candidate type
     * @param beanType  The bean type
     * @param value     The matching value
     * @return true if matches
     */
    static <T> boolean matchByCandidateName(BeanType<T> candidate, Class<T> beanType, String value) {
        String definedCandidateName;
        if (candidate instanceof NameResolver) {
            Optional<String> resolvedName = ((NameResolver) candidate).resolveName();
            definedCandidateName = resolvedName.orElse(candidate.getBeanType().getSimpleName());
        } else {
            definedCandidateName = candidate.getBeanType().getSimpleName();
        }
        return definedCandidateName.equalsIgnoreCase(value) || definedCandidateName.equalsIgnoreCase(value + beanType.getSimpleName());
    }

    /**
     * Check if annotation qualifiers represent the same annotation.
     *
     * @param o1 The annotation object 1
     * @param o2 The annotation object 2
     * @return true if equals
     */
    public static boolean annotationQualifiersEquals(@NonNull Object o1, @NonNull Object o2) {
        Map.Entry<String, Map<CharSequence, Object>> val1 = extractAnnotationAndBindingValues(o1);
        if (val1 == null) {
            return false;
        }
        Map.Entry<String, Map<CharSequence, Object>> val2 = extractAnnotationAndBindingValues(o2);
        if (val2 == null) {
            return false;
        }
        return Objects.equals(val1.getKey(), val2.getKey()) && Objects.equals(val1.getValue(), val2.getValue());
    }

    @Nullable
    private static Map.Entry<String, Map<CharSequence, Object>> extractAnnotationAndBindingValues(@NonNull Object o) {
        if (o instanceof NamedAnnotationStereotypeQualifier) {
            NamedAnnotationStereotypeQualifier<?> that = (NamedAnnotationStereotypeQualifier<?>) o;
            return new AbstractMap.SimpleEntry<>(that.stereotype, null);
        } else if (o instanceof AnnotationStereotypeQualifier) {
            AnnotationStereotypeQualifier<?> that = (AnnotationStereotypeQualifier<?>) o;
            return new AbstractMap.SimpleEntry<>(that.stereotype.getName(), null);
        } else if (o instanceof AnnotationMetadataQualifier) {
            AnnotationMetadataQualifier<?> that = (AnnotationMetadataQualifier<?>) o;
            if (that.qualifierAnn == null) {
                return new AbstractMap.SimpleEntry<>(that.annotationName, null);
            } else {
                return new AbstractMap.SimpleEntry<>(that.annotationName, that.qualifierAnn.getValues());
            }
        } else if (o instanceof AnnotationQualifier) {
            AnnotationQualifier<?> that = (AnnotationQualifier<?>) o;
            return new AbstractMap.SimpleEntry<>(that.annotation.annotationType().getName(), null);
        }
        return null;
    }

}
