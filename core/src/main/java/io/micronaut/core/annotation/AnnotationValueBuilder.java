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

import io.micronaut.core.util.StringUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

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
    public AnnotationValueBuilder<T>  value(@Nullable String str) {
        if (StringUtils.isNotEmpty(str)) {
            values.put(AnnotationMetadata.VALUE_MEMBER, str);
        }
        return this;
    }

    /**
     * Sets the value member to the given String[] values.
     *
     * @param values The String[]
     * @return This builder
     */
    public AnnotationValueBuilder<T>  values(@Nullable String[] values) {
        if (values != null && values.length > 0) {
            this.values.put(AnnotationMetadata.VALUE_MEMBER, values);
        }
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
    public AnnotationValueBuilder<T> value(@Nullable Enum<?> enumObj) {
        if (enumObj != null) {
            values.put(AnnotationMetadata.VALUE_MEMBER, enumObj);
        }
        return this;
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param type The type
     * @return This builder
     */
    public AnnotationValueBuilder<T>  value(@Nullable Class<?> type) {
        if (type != null) {
            values.put(AnnotationMetadata.VALUE_MEMBER, type);
        }
        return this;
    }

    /**
     * Sets the value member to the given integer value.
     *
     * @param name The name of the member
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
     * @param name The name of the member
     * @param str The string
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, String str) {
        if (StringUtils.isNotEmpty(str)) {
            values.put(name, str);
        }
        return this;
    }

    /**
     * Sets the value member to the given boolean value.
     *
     * @param name The name of the member
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
     * @param name The name of the member
     * @param enumObj The enum
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable Enum<?> enumObj) {
        if (enumObj != null) {
            values.put(name, enumObj);
        }
        return this;
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param name The name of the member
     * @param type The type
     * @return This builder
     */
    public AnnotationValueBuilder<T>  member(String name, @Nullable Class<?> type) {
        if (type != null) {
            values.put(name, type);
        }
        return this;
    }

}
