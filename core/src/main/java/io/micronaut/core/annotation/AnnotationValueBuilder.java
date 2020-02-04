/*
 * Copyright 2017-2019 original authors
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
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
    private final Map<CharSequence, Object> values = new HashMap<>(5);
    private final RetentionPolicy retentionPolicy;

    /**
     * Default constructor.
     *
     * @param annotationName The annotation name
     */
    @Internal
    AnnotationValueBuilder(String annotationName) {
        this(annotationName, RetentionPolicy.RUNTIME);
    }

    /**
     * Default constructor.
     *
     * @param annotationName The annotation name
     * @param retentionPolicy The retention policy
     */
    @Internal
    AnnotationValueBuilder(String annotationName, RetentionPolicy retentionPolicy) {
        this.annotationName = annotationName;
        this.retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.RUNTIME;
    }

    /**
     * Default constructor.
     *
     * @param annotation The annotation
     */
    @Internal
    AnnotationValueBuilder(Class<?> annotation) {
        this(annotation.getName());
    }

    /**
     * Default constructor.
     *
     * @param value An existing value
     * @param retentionPolicy The retention policy
     */
    @Internal
    AnnotationValueBuilder(AnnotationValue<T> value, RetentionPolicy retentionPolicy) {
        this.annotationName = value.getAnnotationName();
        this.values.putAll(value.getValues());
        this.retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.RUNTIME;
    }

    /**
     * Build the actual {@link AnnotationValue}.
     *
     * @return The {@link AnnotationValue}
     */
    public @Nonnull AnnotationValue<T> build() {
        if (retentionPolicy != RetentionPolicy.RUNTIME) {
            //noinspection unchecked
            return new AnnotationValue(annotationName, values) {
                @Nonnull
                @Override
                public RetentionPolicy getRetentionPolicy() {
                    return retentionPolicy;
                }
            };
        }
        return new AnnotationValue<>(annotationName, values);
    }

    /**
     * Sets the value member to the given integer value.
     *
     * @param i The integer
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(int i) {
        return member(AnnotationMetadata.VALUE_MEMBER, i);
    }

    /**
     * Sets the value member to the given integer[] value.
     *
     * @param ints The integer[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(int... ints) {
        return member(AnnotationMetadata.VALUE_MEMBER, ints);
    }

    /**
     * Sets the value member to the given long value.
     *
     * @param i The long
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(long i) {
        return member(AnnotationMetadata.VALUE_MEMBER, i);
    }

    /**
     * Sets the value member to the given long[] value.
     *
     * @param longs The long[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(long... longs) {
        return member(AnnotationMetadata.VALUE_MEMBER, longs);
    }

    /**
     * Sets the value member to the given string value.
     *
     * @param str The string
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(@Nullable String str) {
        return member(AnnotationMetadata.VALUE_MEMBER, str);
    }

    /**
     * Sets the value member to the given String[] values.
     *
     * @param strings The String[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(@Nullable String... strings) {
        return member(AnnotationMetadata.VALUE_MEMBER, strings);
    }

    /**
     * Sets the value member to the given boolean value.
     *
     * @param bool The boolean
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(boolean bool) {
        return member(AnnotationMetadata.VALUE_MEMBER, bool);
    }

    /**
     * Sets the value member to the given enum object.
     *
     * @param enumObj The enum
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(@Nullable Enum<?> enumObj) {
        return member(AnnotationMetadata.VALUE_MEMBER, enumObj);
    }

    /**
     * Sets the value member to the given enum objects.
     *
     * @param enumObjs The enum[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(@Nullable Enum<?>... enumObjs) {
        return member(AnnotationMetadata.VALUE_MEMBER, enumObjs);
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param type The type
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(@Nullable Class<?> type) {
        return member(AnnotationMetadata.VALUE_MEMBER, type);
    }

    /**
     * Sets the value member to the given type objects.
     *
     * @param types The type[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(@Nullable Class<?>... types) {
        return member(AnnotationMetadata.VALUE_MEMBER, types);
    }

    /**
     * Sets the value member to the given type objects.
     *
     * @param types The type[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(@Nullable AnnotationClassValue<?>... types) {
        return member(AnnotationMetadata.VALUE_MEMBER, types);
    }

    /**
     * Sets the value member to the given annotation value.
     *
     * @param annotation The annotation
     * @return This builder
     */
    public AnnotationValueBuilder<T> value(@Nullable AnnotationValue<?> annotation) {
        return member(AnnotationMetadata.VALUE_MEMBER, annotation);
    }

    /**
     * Sets the value member to the given annotation values.
     *
     * @param annotations The annotation[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> values(@Nullable AnnotationValue<?>... annotations) {
        return member(AnnotationMetadata.VALUE_MEMBER, annotations);
    }

    /**
     * Sets the value member to the given integer value.
     *
     * @param name The name of the member
     * @param i The integer
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, int i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the value member to the given integer[] value.
     *
     * @param name The name of the member
     * @param ints The integer[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, int... ints) {
        if (ints != null) {
            values.put(name, ints);
        }
        return this;
    }

    /**
     * Sets the value member to the given long value.
     *
     * @param name The name of the member
     * @param i The long
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, long i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the value member to the given long[] value.
     *
     * @param name The name of the member
     * @param longs The long[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, long... longs) {
        if (longs != null) {
            values.put(name, longs);
        }
        return this;
    }

    /**
     * Sets the value member to the given string value.
     *
     * @param name The name of the member
     * @param str The string
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, String str) {
        if (str != null) {
            values.put(name, str);
        }
        return this;
    }

    /**
     * Sets the value member to the given String[] values.
     *
     * @param name The name of the member
     * @param strings The String[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, String... strings) {
        if (strings != null) {
            values.put(name, strings);
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
    public AnnotationValueBuilder<T> member(String name, boolean bool) {
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
     * Sets the value member to the given enum objects.
     *
     * @param name The name of the member
     * @param enumObjs The enum[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable Enum<?>... enumObjs) {
        if (enumObjs != null) {
            values.put(name, enumObjs);
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
    public AnnotationValueBuilder<T> member(String name, @Nullable Class<?> type) {
        if (type != null) {
            values.put(name, new AnnotationClassValue<>(type));
        }
        return this;
    }

    /**
     * Sets the value member to the given type objects.
     *
     * @param name The name of the member
     * @param types The type[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable Class<?>... types) {
        if (types != null) {
            AnnotationClassValue[] classValues = new AnnotationClassValue[types.length];
            for (int i = 0; i < types.length; i++) {
                Class<?> type = types[i];
                classValues[i] = new AnnotationClassValue<>(type);
            }
            values.put(name, classValues);
        }
        return this;
    }

    /**
     * Sets the value member to the given annotation value.
     *
     * @param name The name of the member
     * @param annotation The annotation
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable AnnotationValue<?> annotation) {
        if (annotation != null) {
            values.put(name, annotation);
        }
        return this;
    }

    /**
     * Sets the value member to the given annotation values.
     *
     * @param name The name of the member
     * @param annotations The annotation[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable AnnotationValue<?>... annotations) {
        if (annotations != null) {
            values.put(name, annotations);
        }
        return this;
    }

    /**
     * Sets the value member to the given annotation class values.
     *
     * @param name The name of the member
     * @param classValues The annotation[]
     * @return This builder
     */
    public AnnotationValueBuilder<T> member(String name, @Nullable AnnotationClassValue<?>... classValues) {
        if (classValues != null) {
            values.put(name, classValues);
        }
        return this;
    }
}
