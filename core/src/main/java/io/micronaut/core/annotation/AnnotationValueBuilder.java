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
package io.micronaut.core.annotation;

import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<CharSequence, Object> values = new LinkedHashMap<>(5);
    private final RetentionPolicy retentionPolicy;
    private final List<AnnotationValue<?>> stereotypes = new ArrayList<>();
    private final Map<String, Object> defaultValues = new LinkedHashMap<>();

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
    @NonNull
    public AnnotationValue<T> build() {
        return new AnnotationValue<>(annotationName, values, defaultValues, retentionPolicy, stereotypes);
    }

    /**
     * Adds a stereotype of the annotation.
     *
     * @param annotation The stereotype
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> stereotype(AnnotationValue<?> annotation) {
        if (annotation != null) {
            stereotypes.add(annotation);
        }
        return this;
    }

    /**
     * Sets the default values of the annotation.
     *
     * @param defaultValues The default values
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> defaultValues(Map<String, Object> defaultValues) {
        if (defaultValues != null) {
            this.defaultValues.putAll(defaultValues);
        }
        return this;
    }


    /**
     * Sets the value member to the given integer value.
     *
     * @param i The integer
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(int i) {
        return member(AnnotationMetadata.VALUE_MEMBER, i);
    }

    /**
     * Sets the value member to the given integer[] value.
     *
     * @param ints The integer[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable int... ints) {
        return member(AnnotationMetadata.VALUE_MEMBER, ints);
    }

    /**
     * Sets the value member to the given long value.
     *
     * @param i The long
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(long i) {
        return member(AnnotationMetadata.VALUE_MEMBER, i);
    }

    /**
     * Sets the value member to the given long[] value.
     *
     * @param longs The long[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable long... longs) {
        return member(AnnotationMetadata.VALUE_MEMBER, longs);
    }

    /**
     * Sets the value member to the given string value.
     *
     * @param str The string
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(@Nullable String str) {
        return member(AnnotationMetadata.VALUE_MEMBER, str);
    }

    /**
     * Sets the value member to the given String[] values.
     *
     * @param strings The String[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable String... strings) {
        return member(AnnotationMetadata.VALUE_MEMBER, strings);
    }

    /**
     * Sets the value member to the given boolean value.
     *
     * @param bool The boolean
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(boolean bool) {
        return member(AnnotationMetadata.VALUE_MEMBER, bool);
    }

    /**
     * Sets the value member to the given char value.
     *
     * @param character The char
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> value(char character) {
        return member(AnnotationMetadata.VALUE_MEMBER, character);
    }

    /**
     * Sets the value member to the given double value.
     *
     * @param number The double
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> value(double number) {
        return member(AnnotationMetadata.VALUE_MEMBER, number);
    }

    /**
     * Sets the value member to the given float value.
     *
     * @param f The float
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> value(float f) {
        return member(AnnotationMetadata.VALUE_MEMBER, f);
    }

    /**
     * Sets the value member to the given enum object.
     *
     * @param enumObj The enum
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(@Nullable Enum<?> enumObj) {
        return member(AnnotationMetadata.VALUE_MEMBER, enumObj);
    }

    /**
     * Sets the value member to the given enum objects.
     *
     * @param enumObjs The enum[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable Enum<?>... enumObjs) {
        return member(AnnotationMetadata.VALUE_MEMBER, enumObjs);
    }

    /**
     * Sets the value member to the given type object.
     *
     * @param type The type
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(@Nullable Class<?> type) {
        return member(AnnotationMetadata.VALUE_MEMBER, type);
    }

    /**
     * Sets the value member to the given type objects.
     *
     * @param types The type[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable Class<?>... types) {
        return member(AnnotationMetadata.VALUE_MEMBER, types);
    }

    /**
     * Sets the value member to the given type objects.
     *
     * @param types The type[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable AnnotationClassValue<?>... types) {
        return member(AnnotationMetadata.VALUE_MEMBER, types);
    }

    /**
     * Sets the value member to the given annotation value.
     *
     * @param annotation The annotation
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> value(@Nullable AnnotationValue<?> annotation) {
        return member(AnnotationMetadata.VALUE_MEMBER, annotation);
    }

    /**
     * Sets the value member to the given annotation values.
     *
     * @param annotations The annotation[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> values(@Nullable AnnotationValue<?>... annotations) {
        return member(AnnotationMetadata.VALUE_MEMBER, annotations);
    }

    /**
     * Sets the given member to the given integer value.
     *
     * @param name The name of the member
     * @param i The integer
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, int i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the given member to the given byte value.
     *
     * @param name The name of the member
     * @param b The byte
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, byte b) {
        values.put(name, b);
        return this;
    }

    /**
     * Sets the given member to the given char value.
     *
     * @param name The name of the member
     * @param c The char
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, char c) {
        values.put(name, c);
        return this;
    }

    /**
     * Sets the given member to the given char[] value.
     *
     * @param name The name of the member
     * @param chars The chars
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, char... chars) {
        values.put(name, chars);
        return this;
    }

    /**
     * Sets the given member to the given double value.
     *
     * @param name The name of the member
     * @param d The double
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, double d) {
        values.put(name, d);
        return this;
    }

    /**
     * Sets the given member to the given double[] value.
     *
     * @param name The name of the member
     * @param doubles The double[]
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, double... doubles) {
        values.put(name, doubles);
        return this;
    }

    /**
     * Sets the given member to the given float value.
     *
     * @param name The name of the member
     * @param f The float
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, float f) {
        values.put(name, f);
        return this;
    }

    /**
     * Sets the given member to the given float[] value.
     *
     * @param name The name of the member
     * @param floats The float[]
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, float... floats) {
        values.put(name, floats);
        return this;
    }

    /**
     * Sets the given member to the given integer[] value.
     *
     * @param name The name of the member
     * @param ints The integer[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable int... ints) {
        if (ints != null) {
            values.put(name, ints);
        }
        return this;
    }

    /**
     * Sets the given member to the given byte[] value.
     *
     * @param name The name of the member
     * @param bytes The byte[]
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable byte... bytes) {
        if (bytes != null) {
            values.put(name, bytes);
        }
        return this;
    }

    /**
     * Sets the given member to the given long value.
     *
     * @param name The name of the member
     * @param i The long
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, long i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the given member to the given short value.
     *
     * @param name The name of the member
     * @param i The short
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, short i) {
        values.put(name, i);
        return this;
    }

    /**
     * Sets the given member to the given short[] value.
     *
     * @param name The name of the member
     * @param shorts The short[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, short... shorts) {
        values.put(name, shorts);
        return this;
    }

    /**
     * Sets the given member to the given long[] value.
     *
     * @param name The name of the member
     * @param longs The long[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable long... longs) {
        if (longs != null) {
            values.put(name, longs);
        }
        return this;
    }

    /**
     * Sets the given member to the given string value.
     *
     * @param name The name of the member
     * @param str The string
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable String str) {
        if (str != null) {
            values.put(name, str);
        }
        return this;
    }

    /**
     * Sets the given member to the given String[] values.
     *
     * @param name The name of the member
     * @param strings The String[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable String... strings) {
        if (strings != null) {
            values.put(name, strings);
        }
        return this;
    }

    /**
     * Sets the given member to the given boolean value.
     *
     * @param name The name of the member
     * @param bool The boolean
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, boolean bool) {
        values.put(name, bool);
        return this;
    }

    /**
     * Sets the given member to the given boolean value array.
     *
     * @param name The name of the member
     * @param booleans The booleans
     * @return This builder
     * @since 3.0.0
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, boolean... booleans) {
        values.put(name, booleans);
        return this;
    }

    /**
     * Sets the given member to the given enum object.
     *
     * @param name The name of the member
     * @param enumObj The enum
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable Enum<?> enumObj) {
        if (enumObj != null) {
            values.put(name, enumObj);
        }
        return this;
    }

    /**
     * Sets the given member to the given enum objects.
     *
     * @param name The name of the member
     * @param enumObjs The enum[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable Enum<?>... enumObjs) {
        if (enumObjs != null) {
            values.put(name, enumObjs);
        }
        return this;
    }

    /**
     * Sets the given member to the given type object.
     *
     * @param name The name of the member
     * @param type The type
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable Class<?> type) {
        if (type != null) {
            values.put(name, new AnnotationClassValue<>(type));
        }
        return this;
    }

    /**
     * Sets the given member to the given type objects.
     *
     * @param name The name of the member
     * @param types The type[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable Class<?>... types) {
        if (types != null) {
            AnnotationClassValue<?>[] classValues = new AnnotationClassValue[types.length];
            for (int i = 0; i < types.length; i++) {
                Class<?> type = types[i];
                classValues[i] = new AnnotationClassValue<>(type);
            }
            values.put(name, classValues);
        }
        return this;
    }

    /**
     * Sets the given member to the given annotation value.
     *
     * @param name The name of the member
     * @param annotation The annotation
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable AnnotationValue<?> annotation) {
        if (annotation != null) {
            values.put(name, annotation);
        }
        return this;
    }

    /**
     * Sets the given member to the given annotation values.
     *
     * @param name The name of the member
     * @param annotations The annotation[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable AnnotationValue<?>... annotations) {
        if (annotations != null) {
            values.put(name, annotations);
        }
        return this;
    }

    /**
     * Sets the given member to the given annotation class values.
     *
     * @param name The name of the member
     * @param classValues The annotation[]
     * @return This builder
     */
    @NonNull
    public AnnotationValueBuilder<T> member(@NonNull String name, @Nullable AnnotationClassValue<?>... classValues) {
        if (classValues != null) {
            values.put(name, classValues);
        }
        return this;
    }

    /**
     * Adds the members from the provided map. All values must be primitives, enums,
     * strings, annotation values, or an array of any of the previous types.
     *
     * @param members The map of members
     * @return This builder
     * @since 2.4.0
     */
    @NonNull
    public AnnotationValueBuilder<T> members(@Nullable Map<CharSequence, Object> members) {
        if (members != null) {
            for (Map.Entry<CharSequence, Object> entry: members.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    Class clazz = value.getClass();
                    boolean isArray = clazz.isArray();
                    if (isArray) {
                        clazz = clazz.getComponentType();
                    }
                    boolean isValid = !clazz.isArray() &&
                            (
                                    clazz.isPrimitive() ||
                                    (ReflectionUtils.getPrimitiveType(clazz).isPrimitive() && !isArray) ||
                                    clazz.isEnum() ||
                                    clazz == Class.class ||
                                    clazz == String.class ||
                                    clazz == Enum.class ||
                                    clazz == AnnotationClassValue.class ||
                                    clazz == AnnotationValue.class
                            );
                    if (!isValid) {
                        throw new IllegalArgumentException("The member named [" + entry.getKey().toString() + "] with type [" + value.getClass().getName() + "] is not a valid member type");
                    }
                }
            }
            for (Map.Entry<CharSequence, Object> entry: members.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    Class<?> clazz = value.getClass();
                    String key = entry.getKey().toString();
                    if (clazz == Class.class) {
                        member(key, (Class<?>) value);
                    } else if (clazz.isArray() && clazz.getComponentType() == Class.class) {
                        member(key, (Class<?>[]) value);
                    } else {
                        values.put(key, value);
                    }
                }
            }
        }
        return this;
    }
}
