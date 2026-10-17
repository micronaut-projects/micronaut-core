/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.annotation;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link AnnotationView} over the raw members stored by the annotation metadata. The conversions
 * mirror the ones {@link AnnotationValue} applies when it has no value mapper.
 *
 * @param annotationName The annotation name
 * @param values         The raw members
 */
@Internal
record MapAnnotationView(String annotationName, Map<CharSequence, Object> values) implements AnnotationView {

    @Override
    public boolean isPresent(String member) {
        return StringUtils.isNotEmpty(member) && values.containsKey(member);
    }

    @Override
    public Optional<String> stringValue(String member) {
        Object o = rawSingleValue(member);
        return o != null ? Optional.of(o.toString()) : Optional.empty();
    }

    @Override
    public OptionalInt intValue(String member) {
        Object o = rawSingleValue(member);
        if (o instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return OptionalInt.of(Integer.parseInt(charSequence.toString()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(String member) {
        Object o = rawSingleValue(member);
        if (o instanceof Boolean aBoolean) {
            return Optional.of(aBoolean);
        }
        if (o instanceof CharSequence charSequence) {
            return Optional.of(StringUtils.isTrue(charSequence.toString()));
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(String member) {
        Object o = rawSingleValue(member);
        if (o instanceof Boolean aBoolean) {
            return aBoolean;
        }
        return o != null && StringUtils.isTrue(o.toString());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Class<?>> classValue(String member) {
        Object o = rawSingleValue(member);
        if (o instanceof AnnotationClassValue annotationClassValue) {
            return annotationClassValue.getType();
        }
        if (o instanceof Class<?> aClass) {
            return Optional.of(aClass);
        }
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("enumType", enumType);
        Object o = rawSingleValue(member);
        return o != null ? AnnotationValue.convertToEnum(enumType, o) : Optional.empty();
    }

    private @Nullable Object rawSingleValue(String member) {
        if (StringUtils.isEmpty(member)) {
            return null;
        }
        return AnnotationValue.rawSingleValue(values, member);
    }
}
