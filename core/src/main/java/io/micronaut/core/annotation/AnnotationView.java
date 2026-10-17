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

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A read-only view of an annotation's members that avoids building an {@link AnnotationValue}.
 *
 * <p>The typed accessors return what the same accessor of the {@link AnnotationValue} returned by
 * {@link AnnotationMetadata#findAnnotation(String)} returns. Like those accessors, they read the declared
 * members only: annotation defaults are not applied.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public sealed interface AnnotationView permits MapAnnotationView, AnnotationValueView {

    /**
     * @return The annotation name
     */
    String annotationName();

    /**
     * @param member The member
     * @return Whether the member is present
     */
    boolean isPresent(String member);

    /**
     * @param member The member
     * @return The string value of the member
     */
    Optional<String> stringValue(String member);

    /**
     * @return The string value of the {@code value} member
     */
    default Optional<String> stringValue() {
        return stringValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * @param member The member
     * @return The int value of the member
     */
    OptionalInt intValue(String member);

    /**
     * @param member The member
     * @return The boolean value of the member
     */
    Optional<Boolean> booleanValue(String member);

    /**
     * @param member The member
     * @return Whether the member is true
     */
    boolean isTrue(String member);

    /**
     * @param member The member
     * @return The class value of the member
     */
    Optional<Class<?>> classValue(String member);

    /**
     * @param member   The member
     * @param enumType The enum type
     * @param <E>      The enum type
     * @return The enum value of the member
     */
    <E extends Enum> Optional<E> enumValue(String member, Class<E> enumType);

    /**
     * Creates a view of the raw members of an annotation.
     *
     * @param annotationName The annotation name
     * @param values         The members, as stored by the annotation metadata
     * @return The view
     */
    static AnnotationView of(String annotationName, Map<CharSequence, Object> values) {
        return new MapAnnotationView(annotationName, values);
    }

    /**
     * Creates a view delegating to an annotation value.
     *
     * @param annotationValue The annotation value
     * @return The view
     */
    static AnnotationView of(AnnotationValue<?> annotationValue) {
        return new AnnotationValueView(annotationValue);
    }
}
