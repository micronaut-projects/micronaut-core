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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.core.reflect.ReflectionUtils.EMPTY_CLASS_ARRAY;

/**
 * A runtime representation of the annotation and its values.
 *
 * <p>This class implements the {@link AnnotationValueResolver} interface and methods such as {@link AnnotationValueResolver#get(CharSequence, Class)} can be used to retrieve the values of annotation members.</p>
 *
 * <p>If a member is not present then the methods of the class will attempt to resolve the default value for a given annotation member. In this sense the behaviour of this class is similar to how
 * a implementation of {@link Annotation} behaves.</p>
 *
 * NOTE: During the mapping or remapping, nullable stereotypes value means that
 * the stereotypes will be filled from the annotation definition, when empty collection will skip it.
 *
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationValue<A extends Annotation> implements AnnotationValueResolver {

    private final String annotationName;
    private final ConvertibleValues<Object> convertibleValues;
    private final Map<CharSequence, Object> values;
    @Nullable
    private Map<CharSequence, Object> defaultValues;
    @Nullable
    private final Function<Object, Object> valueMapper;
    private final RetentionPolicy retentionPolicy;
    @Nullable
    private final List<AnnotationValue<?>> stereotypes;
    @Nullable
    private final AnnotationDefaultValuesProvider defaultValuesProvider;

    /**
     * @param annotationName The annotation name
     * @param values         The values
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this(annotationName, values, null, RetentionPolicy.RUNTIME);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param retentionPolicy The retention policy
     */
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        this(annotationName, values, null, retentionPolicy, null);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param retentionPolicy The retention policy
     * @param stereotypes     The stereotypes of the annotation
     */
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy, List<AnnotationValue<?>> stereotypes) {
        this(annotationName, values, null, retentionPolicy, stereotypes);
    }

    /**
     * @param annotationName The annotation name
     * @param values         The values
     * @param defaultValues  The default values
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<CharSequence, Object> defaultValues) {
        this(annotationName, values, defaultValues, RetentionPolicy.RUNTIME, null);
    }

    /**
     * @param annotationName        The annotation name
     * @param values                The values
     * @param defaultValuesProvider The default values provider
     * @since 4.2.0
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, AnnotationDefaultValuesProvider defaultValuesProvider) {
        this(annotationName, values, null, RetentionPolicy.RUNTIME, null, defaultValuesProvider);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param defaultValues   The default values
     * @param retentionPolicy The retention policy
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<CharSequence, Object> defaultValues, RetentionPolicy retentionPolicy) {
        this(annotationName, values, defaultValues, retentionPolicy, null);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param defaultValues   The default values
     * @param retentionPolicy The retention policy
     * @param stereotypes     The stereotypes of the annotation
     */
    @Internal
    public AnnotationValue(String annotationName,
                           Map<CharSequence, Object> values,
                           Map<CharSequence, Object> defaultValues,
                           RetentionPolicy retentionPolicy,
                           List<AnnotationValue<?>> stereotypes) {
        this(annotationName, values, defaultValues, retentionPolicy, stereotypes, null);
    }

    /**
     * @param annotationName        The annotation name
     * @param values                The values
     * @param defaultValues         The default values
     * @param retentionPolicy       The retention policy
     * @param stereotypes           The stereotypes of the annotation
     * @param defaultValuesProvider The defaults values provider
     */
    @Internal
    public AnnotationValue(String annotationName,
                           Map<CharSequence, Object> values,
                           Map<CharSequence, Object> defaultValues,
                           RetentionPolicy retentionPolicy,
                           List<AnnotationValue<?>> stereotypes,
                           AnnotationDefaultValuesProvider defaultValuesProvider) {
        this.annotationName = annotationName;
        this.convertibleValues = newConvertibleValues(values);
        this.values = values;
        this.defaultValues = defaultValues;
        this.valueMapper = null;
        this.retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.RUNTIME;
        this.stereotypes = stereotypes;
        this.defaultValuesProvider = defaultValuesProvider;
    }

    /**
     * @param annotationName The annotation name
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationValue(String annotationName) {
        this(annotationName, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * @param annotationName    The annotation name
     * @param convertibleValues The convertible values
     */
    @Internal
    public AnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        this.annotationName = annotationName;
        this.convertibleValues = convertibleValues;
        this.values = new LinkedHashMap<>(convertibleValues.asMap());
        this.defaultValues = null;
        this.valueMapper = null;
        this.retentionPolicy = RetentionPolicy.RUNTIME;
        this.stereotypes = null;
        this.defaultValuesProvider = null;
    }

    /**
     * @param target            The target
     * @param defaultValues     The default values
     * @param convertibleValues The convertible values
     * @param valueMapper       The value mapper
     */
    @Internal
    @UsedByGeneratedCode
    public AnnotationValue(AnnotationValue<A> target,
                           Map<CharSequence, Object> defaultValues,
                           ConvertibleValues<Object> convertibleValues,
                           Function<Object, Object> valueMapper) {
        this.annotationName = target.annotationName;
        this.defaultValues = defaultValues;
        this.values = target.values;
        this.convertibleValues = convertibleValues;
        this.valueMapper = valueMapper;
        this.retentionPolicy = RetentionPolicy.RUNTIME;
        this.stereotypes = target.stereotypes;
        this.defaultValuesProvider = null;
    }

    /**
     * @return The value mapper.
     * @since 4.0.2
     */
    protected @Nullable Function<Object, Object> getValueMapper() {
        return valueMapper;
    }

    /**
     * Creates a builder with the initial value of this annotation.
     *
     * @return The builder with this annotation value
     * @since 4.0.0
     */
    public AnnotationValueBuilder<A> mutate() {
        return builder(this);
    }

    /**
     * @return The retention policy.
     */
    @NonNull
    public final RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    /**
     * @return The stereotypes of the annotation
     */
    @Nullable
    public List<AnnotationValue<?>> getStereotypes() {
        return stereotypes;
    }

    /**
     * The default values.
     * @return The default of the annotation or null if not specified.
     * @since 4.0.0
     */
    @Nullable
    public Map<CharSequence, Object> getDefaultValues() {
        if (defaultValues == null && defaultValuesProvider != null) {
            defaultValues = defaultValuesProvider.provide(annotationName);
        }
        return defaultValues;
    }

    /**
     * Resolves a map of properties for a member that is an array of annotations that have members called "name" or "key" to represent the key and "value" to represent the value.
     *
     * <p>For example consider the following annotation definition:</p>
     *
     * <pre class="code">
     * &#064;PropertySource({ @Property(name = "one", value = "1"), @Property(name = "two", value = "2")})
     * public class MyBean {
     *        ...
     * }</pre>
     *
     * <p>You can use this method to resolve the values of the {@code PropertySource} annotation such that the following assertion is true:</p>
     *
     * <pre class="code">
     * annotationValue.getProperties("value") == [one:1, two:2]
     * </pre>
     *
     * @param member The member
     * @return The properties as an immutable map.
     */
    @NonNull
    public Map<String, String> getProperties(@NonNull String member) {
        return getProperties(member, "name");
    }

    /**
     * Resolve properties with a custom key member.
     *
     * @param member    The member to resolve the properties from
     * @param keyMember The member of the sub annotation that represents the key.
     * @return The properties.
     * @see #getProperties(String)
     */
    public Map<String, String> getProperties(@NonNull String member, String keyMember) {
        ArgumentUtils.requireNonNull("keyMember", keyMember);
        if (StringUtils.isEmpty(member)) {
            return Collections.emptyMap();
        }
        List<AnnotationValue<Annotation>> values = getAnnotations(member);
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyMap();
        }
        Map<String, String> props = CollectionUtils.newLinkedHashMap(values.size());
        for (AnnotationValue<Annotation> av : values) {
            String name = av.stringValue(keyMember).orElse(null);
            if (StringUtils.isNotEmpty(name)) {
                av.stringValue(AnnotationMetadata.VALUE_MEMBER, valueMapper).ifPresent(v -> props.put(name, v));
            }
        }
        return Collections.unmodifiableMap(props);
    }

    /**
     * Return the enum value of the given member of the given enum type.
     *
     * @param member   The annotation member
     * @param enumType The required type
     * @param <E>      The enum type
     * @return An {@link Optional} of the enum value
     */
    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull String member, @NonNull Class<E> enumType) {
        return enumValue(member, enumType, valueMapper);
    }

    /**
     * Return the enum value of the given member of the given enum type.
     *
     * @param member      The annotation member
     * @param enumType    The required type
     * @param valueMapper The value mapper
     * @param <E>         The enum type
     * @return An {@link Optional} of the enum value
     */
    public <E extends Enum> Optional<E> enumValue(@NonNull String member, @NonNull Class<E> enumType, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("enumType", enumType);
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o != null) {
            return convertToEnum(enumType, o);
        }
        return Optional.empty();
    }

    /**
     * Return the enum values of the given member of the given enum type.
     *
     * @param member   The annotation member
     * @param enumType The required type
     * @param <E>      The enum type
     * @return An array of enum values
     */
    @Override
    @SuppressWarnings("unchecked")
    public <E extends Enum> E[] enumValues(@NonNull String member, @NonNull Class<E> enumType) {
        ArgumentUtils.requireNonNull("enumType", enumType);
        if (StringUtils.isEmpty(member)) {
            return (E[]) Array.newInstance(enumType, 0);
        }
        Object rawValue = values.get(member);
        return resolveEnumValues(enumType, rawValue);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @return An {@link Optional} class
     */
    @Override
    @NonNull
    public Optional<Class<?>> classValue() {
        return classValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member The annotation member
     * @return An {@link Optional} class
     */
    @Override
    public Optional<Class<?>> classValue(@NonNull String member) {
        return classValue(member, valueMapper);
    }

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The required type
     * @return An {@link Optional} class
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Class<? extends T>> classValue(@NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("requiredType", requiredType);
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof AnnotationClassValue<?> annotationClassValue) {
            Class<?> t = annotationClassValue.getType().orElse(null);
            if (t != null && requiredType.isAssignableFrom(t)) {
                return Optional.of((Class<? extends T>) t);
            }
            return Optional.empty();
        }
        if (o instanceof Class<?> t) {
            if (requiredType.isAssignableFrom(t)) {
                return Optional.of((Class<? extends T>) t);
            }
            return Optional.empty();
        }
        if (o != null) {
            Class<?> t = ClassUtils.forName(o.toString(), getClass().getClassLoader()).orElse(null);
            if (t != null && requiredType.isAssignableFrom(t)) {
                return Optional.of((Class<? extends T>) t);
            }
        }
        return Optional.empty();
    }

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member      The annotation member
     * @param valueMapper The raw value mapper
     * @return An {@link Optional} class
     */
    public Optional<Class<?>> classValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof AnnotationClassValue annotationClassValue) {
            return annotationClassValue.getType();
        } else if (o instanceof Class<?> aClass) {
            return Optional.of(aClass);
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull String member) {
        Function<Object, Object> valueMapper = this.valueMapper;
        return stringValues(member, valueMapper);
    }

    @Override
    public boolean[] booleanValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_BOOLEAN_ARRAY;
        }
        if (v instanceof boolean[] booleans) {
            return booleans;
        }
        if (v instanceof Boolean aBoolean) {
            return new boolean[]{aBoolean};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_BOOLEAN_ARRAY;
        }
        boolean[] booleans = new boolean[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            booleans[i] = Boolean.parseBoolean(string);
        }
        return booleans;
    }

    @Override
    public byte[] byteValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
        if (v instanceof byte[] bytes) {
            return bytes;
        }
        if (v instanceof Number number) {
            return new byte[]{number.byteValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
        byte[] bytes = new byte[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            bytes[i] = Byte.parseByte(string);
        }
        return bytes;
    }

    @Override
    public char[] charValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_CHAR_ARRAY;
        }
        if (v instanceof char[] chars) {
            return chars;
        }
        if (v instanceof Character[] v2) {
            char[] chars = new char[v2.length];
            for (int i = 0; i < v2.length; i++) {
                Character character = v2[i];
                chars[i] = character;
            }
            return chars;
        }
        if (v instanceof Character character) {
            return new char[]{character};
        }
        return ArrayUtils.EMPTY_CHAR_ARRAY;
    }

    @Override
    public int[] intValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_INT_ARRAY;
        }
        if (v instanceof int[] ints) {
            return ints;
        }
        if (v instanceof Number number) {
            return new int[]{number.intValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_INT_ARRAY;
        }
        int[] integers = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            integers[i] = Integer.parseInt(string);
        }
        return integers;
    }

    @Override
    public double[] doubleValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_DOUBLE_ARRAY;
        }
        if (v instanceof double[] doubles) {
            return doubles;
        }
        if (v instanceof Number number) {
            return new double[]{number.doubleValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_DOUBLE_ARRAY;
        }
        double[] doubles = new double[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            doubles[i] = Double.parseDouble(string);
        }
        return doubles;
    }

    @Override
    public long[] longValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_LONG_ARRAY;
        }
        if (v instanceof long[] longs) {
            return longs;
        }
        if (v instanceof Number number) {
            return new long[]{number.longValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_LONG_ARRAY;
        }
        long[] longs = new long[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            longs[i] = Long.parseLong(string);
        }
        return longs;
    }

    @Override
    public float[] floatValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_FLOAT_ARRAY;
        }
        if (v instanceof float[] floats) {
            return floats;
        }
        if (v instanceof Number number) {
            return new float[]{number.floatValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_FLOAT_ARRAY;
        }
        float[] floats = new float[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            floats[i] = Float.parseFloat(string);
        }
        return floats;
    }

    @Override
    public short[] shortValues(String member) {
        Object v = values.get(member);
        if (v == null) {
            return ArrayUtils.EMPTY_SHORT_ARRAY;
        }
        if (v instanceof short[] shorts) {
            return shorts;
        }
        if (v instanceof Number number) {
            return new short[]{number.shortValue()};
        }
        String[] strings = resolveStringValues(v, this.valueMapper);
        if (ArrayUtils.isEmpty(strings)) {
            return ArrayUtils.EMPTY_SHORT_ARRAY;
        }
        short[] shorts = new short[strings.length];
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            shorts[i] = Short.parseShort(string);
        }
        return shorts;
    }

    /**
     * The string values for the given member and mapper.
     *
     * @param member      The member
     * @param valueMapper The mapper
     * @return The string values
     */
    public String[] stringValues(@NonNull String member, Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return StringUtils.EMPTY_STRING_ARRAY;
        }
        Object o = values.get(member);
        String[] strs = resolveStringValues(o, valueMapper);
        if (strs != null) {
            return strs;
        }
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public Class<?>[] classValues(@NonNull String member) {
        if (StringUtils.isEmpty(member)) {
            return EMPTY_CLASS_ARRAY;
        }
        Object o = values.get(member);
        Class<?>[] type = resolveClassValues(o);
        if (type != null) {
            return type;
        }
        return EMPTY_CLASS_ARRAY;
    }

    @NonNull
    @Override
    public AnnotationClassValue<?>[] annotationClassValues(@NonNull String member) {
        if (StringUtils.isEmpty(member)) {
            return AnnotationClassValue.ZERO_ANNOTATION_CLASS_VALUES;
        }
        Object o = values.get(member);
        if (o instanceof AnnotationClassValue<?> annotationClassValue) {
            return new AnnotationClassValue[]{annotationClassValue};
        }
        if (o instanceof AnnotationClassValue<?>[] annotationClassValues) {
            return annotationClassValues;
        }
        if (o instanceof String className) {
            return new AnnotationClassValue[] { getAnnotationClassValue(className) };
        }
        if (o instanceof String[] classNames) {
            return Arrays.stream(classNames).map(AnnotationValue::getAnnotationClassValue).toArray(AnnotationClassValue[]::new);
        }
        return AnnotationClassValue.ZERO_ANNOTATION_CLASS_VALUES;
    }

    @Override
    public Optional<AnnotationClassValue<?>> annotationClassValue(@NonNull String member) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = values.get(member);
        if (o instanceof AnnotationClassValue<?> annotationClassValue) {
            annotationClassValue.failIfError();
            return Optional.of(annotationClassValue);
        }
        if (o instanceof AnnotationClassValue<?>[] annotationClassValues) {
            if (annotationClassValues.length > 0) {
                AnnotationClassValue<?> acv = annotationClassValues[0];
                acv.failIfError();
                return Optional.of(acv);
            }
        }
        if (o instanceof String className) {
            return Optional.of(getAnnotationClassValue(className));
        }
        if (o instanceof String[] classNames && classNames.length > 0) {
            return Optional.of(getAnnotationClassValue(classNames[0]));
        }
        return Optional.empty();
    }

    /**
     * The integer value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalInt}
     */
    @Override
    public OptionalInt intValue(@NonNull String member) {
        return intValue(member, valueMapper);
    }

    /**
     * The integer value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link OptionalInt}
     */
    public OptionalInt intValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return OptionalInt.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }
        if (o instanceof String s) {
            try {
                return OptionalInt.of(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
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
    public Optional<Byte> byteValue(String member) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return Optional.of(number.byteValue());
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return Optional.of(Byte.parseByte(charSequence.toString()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Character> charValue(String member) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Character character) {
            return Optional.of(character);
        }
        return Optional.empty();
    }

    /**
     * The integer value of the given member.
     *
     * @return An {@link OptionalInt}
     */
    @Override
    public OptionalInt intValue() {
        return intValue(AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    public OptionalLong longValue(@NonNull String member) {
        return longValue(member, valueMapper);
    }

    /**
     * The long value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link OptionalLong}
     */
    public OptionalLong longValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return OptionalLong.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return OptionalLong.of(number.longValue());
        }
        if (o instanceof String s) {
            try {
                return OptionalLong.of(Long.parseLong(s));
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return OptionalLong.of(Long.parseLong(charSequence.toString()));
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<Short> shortValue(@NonNull String member) {
        return shortValue(member, valueMapper);
    }

    /**
     * The short value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link Optional} of {@link Short}
     */
    public Optional<Short> shortValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return Optional.of(number.shortValue());
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return Optional.of(Short.parseShort(charSequence.toString()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * The boolean value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link Optional} boolean
     */
    public Optional<Boolean> booleanValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Boolean aBoolean) {
            return Optional.of(aBoolean);
        }
        if (o instanceof String s) {
            return Optional.of(StringUtils.isTrue(s));
        }
        if (o instanceof CharSequence charSequence) {
            return Optional.of(StringUtils.isTrue(charSequence.toString()));
        }
        return Optional.empty();
    }

    /**
     * The double value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalDouble}
     */
    @Override
    public OptionalDouble doubleValue(@NonNull String member) {
        return doubleValue(member, valueMapper);
    }

    /**
     * The double value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link OptionalDouble}
     */
    public OptionalDouble doubleValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return OptionalDouble.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }
        if (o instanceof String s) {
            try {
                return OptionalDouble.of(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return OptionalDouble.of(Double.parseDouble(charSequence.toString()));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public Optional<Float> floatValue(String member) {
        return floatValue(member, valueMapper);
    }

    /**
     * The double value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper The value mapper
     * @return An {@link OptionalDouble}
     */
    public Optional<Float> floatValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Number number) {
            return Optional.of(number.floatValue());
        }
        if (o instanceof CharSequence charSequence) {
            try {
                return Optional.of(Float.parseFloat(charSequence.toString()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * The double value of the given member.
     *
     * @return An {@link OptionalDouble}
     */
    @Override
    public OptionalDouble doubleValue() {
        return doubleValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The string value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalInt}
     */
    @Override
    public Optional<String> stringValue(@NonNull String member) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o != null) {
            return Optional.of(o.toString());
        }
        return Optional.empty();
    }

    /**
     * The string value of the given member.
     *
     * @param member      The annotation member
     * @param valueMapper An optional raw value mapper
     * @return An {@link OptionalInt}
     */
    public Optional<String> stringValue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return Optional.empty();
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o != null) {
            return Optional.of(o.toString());
        }
        return Optional.empty();
    }

    /**
     * The double value of the given member.
     *
     * @return An {@link OptionalInt}
     */
    @Override
    public Optional<String> stringValue() {
        return stringValue(AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull String member) {
        return booleanValue(member, valueMapper);
    }

    /**
     * Is the given member present.
     *
     * @param member The member
     * @return True if it is
     */
    @Override
    public final boolean isPresent(CharSequence member) {
        if (StringUtils.isNotEmpty(member)) {
            return values.containsKey(member);
        }
        return false;
    }

    /**
     * @return Is the value of the annotation true.
     */
    @Override
    public boolean isTrue() {
        return isTrue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * @param member The member
     * @return Is the value of the annotation true.
     */
    @Override
    public boolean isTrue(String member) {
        return isTrue(member, valueMapper);
    }

    /**
     * @param member      The member
     * @param valueMapper The value mapper
     * @return Is the value of the annotation true.
     */
    public boolean isTrue(@NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        if (StringUtils.isEmpty(member)) {
            return false;
        }
        Object o = getRawSingleValue(member, valueMapper);
        if (o instanceof Boolean aBoolean) {
            return aBoolean;
        } else if (o != null) {
            return StringUtils.isTrue(o.toString());
        }
        return false;
    }


    /**
     * @return Is the value of the annotation true.
     */
    @Override
    public boolean isFalse() {
        return !isTrue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * @param member The member
     * @return Is the value of the annotation true.
     */
    @Override
    public boolean isFalse(String member) {
        return !isTrue(member);
    }

    /**
     * The annotation name.
     *
     * @return The annotation name
     */
    @NonNull
    public final String getAnnotationName() {
        return annotationName;
    }

    /**
     * Whether a particular member is present.
     *
     * @param member The member
     * @return True if it is
     */
    public final boolean contains(String member) {
        return isPresent(member);
    }

    /**
     * Resolves the names of all the present annotation members.
     *
     * @return The names of the members
     */
    @NonNull
    public final Set<CharSequence> getMemberNames() {
        return values.keySet();
    }

    /**
     * @return The attribute values
     */
    @Override
    @NonNull
    public Map<CharSequence, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * @return The convertible values
     */
    @NonNull
    public ConvertibleValues<Object> getConvertibleValues() {
        return convertibleValues;
    }

    @Override
    public <T> Optional<T> get(CharSequence member, ArgumentConversionContext<T> conversionContext) {
        Optional<T> result = convertibleValues.get(member, conversionContext);
        if (result.isPresent()) {
            return result;
        }
        Map<CharSequence, Object> defaults = getDefaultValues();
        if (defaults != null) {
            Object dv = defaults.get(member.toString());
            if (dv != null) {
                return ConversionService.SHARED.convert(dv, conversionContext);
            }
        }
        return result;
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param conversionContext The conversion context
     * @param <T>               The type
     * @return The result
     */
    public <T> Optional<T> getValue(ArgumentConversionContext<T> conversionContext) {
        return get(AnnotationMetadata.VALUE_MEMBER, conversionContext);
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param argument The argument
     * @param <T>      The type
     * @return The result
     */
    public final <T> Optional<T> getValue(Argument<T> argument) {
        return getValue(ConversionContext.of(argument));
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param type The type
     * @param <T>  The type
     * @return The result
     */
    public final <T> Optional<T> getValue(Class<T> type) {
        return getValue(ConversionContext.of(type));
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param type The type
     * @param <T>  The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given type
     */
    @NonNull
    public final <T> T getRequiredValue(Class<T> type) {
        return getRequiredValue(AnnotationMetadata.VALUE_MEMBER, type);
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param member The member
     * @param type   The type
     * @param <T>    The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     */
    @NonNull
    public final <T> T  getRequiredValue(String member, Class<T> type) {
        return get(member, ConversionContext.of(type)).orElseThrow(() -> new IllegalStateException("No value available for annotation member @" + annotationName + "[" + member + "] of type: " + type));
    }

    /**
     * Gets a list of {@link AnnotationValue} for the given member.
     *
     * @param member The member
     * @param type   The type
     * @param <T>    The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     */
    @NonNull
    @SuppressWarnings("java:S2259") // false positive
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member, Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        String typeName = type.getName();

        ArgumentUtils.requireNonNull("member", member);
        Object v = values.get(member);
        Collection<AnnotationValue<?>> values = null;
        if (v instanceof AnnotationValue<?> annotationValue) {
            values = Collections.singletonList(annotationValue);
        } else if (v instanceof AnnotationValue<?>[] annotationValues) {
            values = Arrays.asList(annotationValues);
        } else if (v instanceof Collection<?> collection) {
            final Iterator<?> i = collection.iterator();
            if (i.hasNext()) {
                final Object o = i.next();
                if (o instanceof AnnotationValue) {
                    values = (Collection<AnnotationValue<?>>) collection;
                }
            }
        }
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        } else {
            List<AnnotationValue<T>> list = new ArrayList<>(values.size());
            for (AnnotationValue<?> value : values) {
                if (value == null) {
                    continue;
                }
                if (value.getAnnotationName().equals(typeName)) {
                    list.add((AnnotationValue<T>) value);
                }
            }
            return list;
        }
    }

    /**
     * Gets a list of {@link AnnotationValue} for the given member.
     *
     * @param member The member
     * @param <T>    The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member) {
        ArgumentUtils.requireNonNull("member", member);
        Object v = values.get(member);
        if (v instanceof AnnotationValue annotationValue) {
            return Collections.singletonList(annotationValue);
        }
        if (v instanceof AnnotationValue[] annotationValues) {
            return Arrays.asList(annotationValues);
        }
        if (v instanceof Collection<?> collection) {
            final Iterator<?> i = collection.iterator();
            if (i.hasNext()) {
                final Object o = i.next();
                if (o instanceof AnnotationValue) {
                    return new ArrayList<>((Collection<? extends AnnotationValue<T>>) v);
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Gets a list of {@link AnnotationValue} for the given member.
     *
     * @param member The member
     * @param type   The type
     * @param <T>    The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     */
    @NonNull
    public <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(String member, Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        String typeName = type.getName();

        ArgumentUtils.requireNonNull("member", member);
        Object v = values.get(member);
        if (v instanceof AnnotationValue av) {
            if (av.getAnnotationName().equals(typeName)) {
                return Optional.of(av);
            }
            return Optional.empty();
        }
        if (v instanceof AnnotationValue[] values) {
            if (ArrayUtils.isNotEmpty(values)) {
                final AnnotationValue value = values[0];
                if (value.getAnnotationName().equals(typeName)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Gets a list of {@link AnnotationValue} for the given member.
     *
     * @param member The member
     * @param <T>    The type
     * @return The result
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     * @since 3.3.0
     */
    @NonNull
    public <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(@NonNull String member) {
        ArgumentUtils.requireNonNull("member", member);
        Object v = values.get(member);
        if (v instanceof AnnotationValue av) {
            return Optional.of(av);
        }
        if (v instanceof AnnotationValue[] values) {
            if (ArrayUtils.isNotEmpty(values)) {
                return Optional.of(values[0]);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * If this AnnotationValue contains Evaluated Expressions.
     *
     * @return true if it is
     * @since 4.0.0
     */
    public boolean hasEvaluatedExpressions() {
        return values.values().stream()
            .anyMatch(value -> value instanceof EvaluatedExpression);
    }

    @Override
    public String toString() {
        if (values.isEmpty()) {
            return "@" + annotationName;
        } else {
            return "@" + annotationName + "(" + values.entrySet().stream().map(entry -> entry.getKey() + "=" + toStringValue(entry.getValue())).collect(
                    Collectors.joining(", ")) + ")";
        }
    }

    private String toStringValue(Object object) {
        if (object == null) {
            return "null";
        }
        if (object instanceof Object[] object1s) {
            return Arrays.deepToString(object1s);
        }
        return object.toString();
    }

    @Override
    public int hashCode() {
        return 31 * annotationName.hashCode() + AnnotationUtil.calculateHashCode(getValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AnnotationValue<?> other)) {
            return false;
        }

        if (!annotationName.equals(other.getAnnotationName())) {
            return false;
        }

        Map<CharSequence, Object> otherValues = other.getValues();
        Map<CharSequence, Object> values = getValues();
        if (values.size() != otherValues.size()) {
            return false;
        }

        // compare annotation member values
        for (Map.Entry<CharSequence, Object> member : values.entrySet()) {
            Object value = member.getValue();
            Object otherValue = otherValues.get(member.getKey());

            if (!AnnotationUtil.areEqual(value, otherValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Start building a new annotation for the given name.
     *
     * @param annotationName The annotation name
     * @param <T>            The annotation type
     * @return The builder
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(String annotationName) {
        return new AnnotationValueBuilder<>(annotationName);
    }

    /**
     * Start building a new annotation for the given name.
     *
     * @param annotationName  The annotation name
     * @param retentionPolicy The retention policy
     * @param <T>             The annotation type
     * @return The builder
     * @since 2.4.0
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(String annotationName, RetentionPolicy retentionPolicy) {
        return new AnnotationValueBuilder<>(annotationName, retentionPolicy);
    }

    /**
     * Start building a new annotation for the given name.
     *
     * @param annotation The annotation name
     * @param <T>        The annotation type
     * @return The builder
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(Class<T> annotation) {
        return new AnnotationValueBuilder<>(annotation);
    }

    /**
     * Start building a new annotation existing value.
     *
     * @param annotation      The annotation name
     * @param <T>             The annotation type
     * @return The builder
     * @since 4.0.0
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(@NonNull AnnotationValue<T> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return new AnnotationValueBuilder<>(annotation, annotation.getRetentionPolicy());
    }

    /**
     * Start building a new annotation existing value and retention policy.
     *
     * @param annotation      The annotation name
     * @param retentionPolicy The retention policy. Defaults to runtime.
     * @param <T>             The annotation type
     * @return The builder
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(@NonNull AnnotationValue<T> annotation, @Nullable RetentionPolicy retentionPolicy) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return new AnnotationValueBuilder<>(annotation, retentionPolicy);
    }

    /**
     * The string values for the given value.
     *
     * @param value       The value
     * @param valueMapper The value mapper
     * @return The string[] or null
     */
    @Internal
    @Nullable
    public static String[] resolveStringValues(@Nullable Object value, @Nullable Function<Object, Object> valueMapper) {
        if (value == null) {
            return null;
        }
        if (valueMapper != null) {
            value = valueMapper.apply(value);
        }
        if (value instanceof String s) {
            return new String[]{s};
        }
        if (value instanceof String[] existing) {
            return Arrays.copyOf(existing, existing.length);
        }
        if (value instanceof CharSequence) {
            return new String[]{value.toString()};
        }
        if (value != null) {
            if (value.getClass().isArray()) {
                int len = Array.getLength(value);
                String[] newArray = new String[len];
                for (int i = 0; i < newArray.length; i++) {
                    Object entry = Array.get(value, i);
                    if (entry != null) {
                        newArray[i] = entry.toString();
                    }
                }
                return newArray;
            } else {
                return new String[]{value.toString()};
            }
        }
        return null;
    }

    /**
     * The enum values for the given enum type and raw value.
     *
     * @param enumType The enum type
     * @param rawValue The raw value
     * @param <E>      The enum generic type
     * @return An array of enum values
     */
    @Internal
    @NonNull
    public static <E extends Enum> E[] resolveEnumValues(@NonNull Class<E> enumType, @Nullable Object rawValue) {
        if (rawValue == null) {
            return (E[]) Array.newInstance(enumType, 0);
        }
        List<E> list = new ArrayList<>();
        if (rawValue.getClass().isArray()) {
            int len = Array.getLength(rawValue);
            for (int i = 0; i < len; i++) {
                convertToEnum(enumType, Array.get(rawValue, i)).ifPresent(list::add);
            }
        } else if (rawValue instanceof Iterable<?> iterable) {
            for (Object o : iterable) {
                convertToEnum(enumType, o).ifPresent(list::add);
            }
        } else if (enumType.isAssignableFrom(rawValue.getClass())) {
            list.add((E) rawValue);
        } else {
            convertToEnum(enumType, rawValue).ifPresent(list::add);
        }
        return list.toArray((E[]) Array.newInstance(enumType, 0));
    }

    /**
     * The string[] values for the given value.
     *
     * @param strs        The strings
     * @param valueMapper The value mapper
     * @return The string[] or the original string
     */
    @Internal
    public static String[] resolveStringArray(String[] strs, @Nullable Function<Object, Object> valueMapper) {
        if (valueMapper != null) {
            String[] newStrs = new String[strs.length];
            for (int i = 0; i < strs.length; i++) {
                String str = strs[i];
                newStrs[i] = valueMapper.apply(str).toString();
            }
            return newStrs;
        } else {
            return strs;
        }
    }

    /**
     * The class values for the given value.
     *
     * @param value The value
     * @return The class values or null
     */
    @Internal
    @Nullable
    public static Class<?>[] resolveClassValues(@Nullable Object value) {
        // conditional branches ordered from most likely to least likely
        // generally at runtime values are always AnnotationClassValue
        // A class can be present at compilation time
        if (value == null) {
            return null;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            Class<?> type = annotationClassValue.getType().orElse(null);
            if (type != null) {
                return new Class<?>[]{type};
            }
            return null;
        }
        if (value instanceof AnnotationValue<?>[] annotationValues) {
            int len = annotationValues.length;
            if (len > 0) {
                if (len == 1) {
                    return annotationValues[0].classValues();
                } else {
                    List<Class<?>> list = new ArrayList<>(5);
                    for (AnnotationValue<?> annotationValue : annotationValues) {
                        list.addAll(Arrays.asList(annotationValue.classValues()));
                    }
                    return list.toArray(EMPTY_CLASS_ARRAY);
                }
            }
            return null;
        }
        if (value instanceof AnnotationValue<?> annotationValue) {
            return annotationValue.classValues();
        }
        if (value instanceof Object[] values) {
            if (values instanceof Class<?>[] classes) {
                return classes;
            } else {
                List<Class<?>> list = new ArrayList<>(5);
                for (Object o : values) {
                    if (o instanceof AnnotationClassValue<?> annotationClassValue) {
                        if (annotationClassValue.theClass != null) {
                            list.add(annotationClassValue.theClass);
                        }
                    } else if (o instanceof Class<?> aClass) {
                        list.add(aClass);
                    }
                }
                return list.toArray(EMPTY_CLASS_ARRAY);

            }
        }
        if (value instanceof Class<?> aClass) {
            return new Class<?>[]{aClass};
        }
        return null;
    }

    /**
     * Subclasses can override to provide a custom convertible values instance.
     *
     * @param values The values
     * @return The instance
     */
    private ConvertibleValues<Object> newConvertibleValues(Map<CharSequence, Object> values) {
        if (CollectionUtils.isEmpty(values)) {
            return ConvertibleValues.EMPTY;
        } else {
            return ConvertibleValues.of(values);
        }
    }

    @Nullable
    private Object getRawSingleValue(@NonNull String member, Function<Object, Object> valueMapper) {
        Object rawValue = values.get(member);
        if (rawValue != null) {
            if (rawValue.getClass().isArray()) {
                int len = Array.getLength(rawValue);
                if (len > 0) {
                    rawValue = Array.get(rawValue, 0);
                }
            } else if (rawValue instanceof Iterable<?> iterable) {
                Iterator<?> i = iterable.iterator();
                if (i.hasNext()) {
                    rawValue = i.next();
                }
            }
        }
        if (valueMapper != null && (rawValue instanceof String || rawValue instanceof EvaluatedExpression)) {
            return valueMapper.apply(rawValue);
        }
        return rawValue;
    }

    private static <T extends Enum> Optional<T> convertToEnum(Class<T> enumType, Object o) {
        if (enumType.isInstance(o)) {
            return Optional.of((T) o);
        } else {
            try {
                T t = (T) Enum.valueOf(enumType, o.toString());
                return Optional.of(t);
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Creates {@link AnnotationClassValue} from the class name.
     *
     * @param className The class name
     * @return AnnotationClassValue for given class name
     */
    private static AnnotationClassValue<?> getAnnotationClassValue(String className) {
        Optional<Class<?>> theClass = ClassUtils.forName(className, null);
        if (theClass.isPresent()) {
            return new AnnotationClassValue<>(theClass.get());
        }
        return new AnnotationClassValue<>(className);
    }
}
