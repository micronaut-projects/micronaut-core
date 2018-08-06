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

package io.micronaut.core.annotation;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A build for annotation values.
 *
 * @param <T> The annotation type
 * @author graemerocher
 * @since 1.0
 */
public class AnnotationValueBuilder<T extends Annotation> {

    private final String annotationName;
    private Map<CharSequence, Object> values = new HashMap<>();

    /**
     * Default constructor.
     *
     * @param annotationName The annotation name
     */
    AnnotationValueBuilder(String annotationName) {
        this.annotationName = annotationName;
    }


    /**
     * Default constructor.
     *
     * @param annotation The annotation
     */
    AnnotationValueBuilder(Class<?> annotation) {
        this.annotationName = annotation.getName();
    }

    /**
     * Build the actual {@link AnnotationValue}.
     *
     * @return The {@link AnnotationValue}
     */
    public AnnotationValue<T> build() {
        return new AnnotationValue<>(annotationName, values);
    }

    /**
     * Sets the value member to the given integer value.
     *
     * @param i The integer
     * @return This builder
     */
    public AnnotationValueBuilder<T>  value(int i) {
        values.put(AnnotationMetadata.VALUE_MEMBER, i);
        return this;
    }

    /**
     * Sets the value member to the given string value.
     *
     * @param str The string
     * @return This builder
     */
    public AnnotationValueBuilder<T>  value(String str) {
        values.put(AnnotationMetadata.VALUE_MEMBER, str);
        return this;
    }

    /**
     * Sets the value member to the given boolean value.
     *
     * @param bool The boolean
     * @return This builder
     */
    public AnnotationValueBuilder<T>  value(boolean bool) {
        values.put(AnnotationMetadata.VALUE_MEMBER, bool);
        return this;
    }

    /**
     * Sets the value member to the given enum object.
     *
     * @param enumObj The enum
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(Enum<?> enumObj) {
        values.put(AnnotationMetadata.VALUE_MEMBER, enumObj);
        return this;
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param type The type
     * @return This builder
     */
    public AnnotationValueBuilder<T>  value(Class<?> type) {
        values.put(AnnotationMetadata.VALUE_MEMBER, type);
        return this;
    }

    /**
     * Sets the value member to the given integer value.
     *
     * @param i The integer
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, int i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the value member to the given string value.
     *
     * @param str The string
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, String str) {
        values.put(name, str);
        return this;
    }

    /**
     * Sets the value member to the given boolean value.
     *
     * @param bool The boolean
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, boolean bool) {
        values.put(name, bool);
        return this;
    }

    /**
     * Sets the value member to the given enum object.
     *
     * @param enumObj The enum
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, Enum<?> enumObj) {
        values.put(name, enumObj);
        return this;
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param type The type
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, Class<?> type) {
        values.put(name, type);
        return this;
    }

}
