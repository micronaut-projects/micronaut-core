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

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link AnnotationView} delegating to an {@link AnnotationValue}, used where the annotation metadata
 * has no faster way to provide the members (merged hierarchies, repeatables, mapped values).
 *
 * @param value The annotation value
 */
@Internal
record AnnotationValueView(AnnotationValue<?> value) implements AnnotationView {

    @Override
    public String annotationName() {
        return value.getAnnotationName();
    }

    @Override
    public boolean isPresent(String member) {
        return value.isPresent(member);
    }

    @Override
    public Optional<String> stringValue(String member) {
        return value.stringValue(member);
    }

    @Override
    public OptionalInt intValue(String member) {
        return value.intValue(member);
    }

    @Override
    public Optional<Boolean> booleanValue(String member) {
        return value.booleanValue(member);
    }

    @Override
    public boolean isTrue(String member) {
        return value.isTrue(member);
    }

    @Override
    public Optional<Class<?>> classValue(String member) {
        return value.classValue(member);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(String member, Class<E> enumType) {
        return value.enumValue(member, enumType);
    }
}
