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
package io.micronaut.inject.annotation;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link AnnotationMetadata}.
 *
 * <p>
 * NOTE: Although required to be public This is an internal class and should not be referenced directly in user code
 * </p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultAnnotationMetadata extends AbstractAnnotationMetadata implements AnnotationMetadata, Cloneable, EnvironmentAnnotationMetadata {

    @Nullable
    Map<String, Map<CharSequence, Object>> declaredAnnotations;
    @Nullable
    Map<String, Map<CharSequence, Object>> allAnnotations;
    @Nullable
    Map<String, Map<CharSequence, Object>> declaredStereotypes;
    @Nullable
    Map<String, Map<CharSequence, Object>> allStereotypes;
    @Nullable
    Map<String, List<String>> annotationsByStereotype;

    private final Map<String, List> annotationValuesByType = new ConcurrentHashMap<>(2);

    private final boolean hasPropertyExpressions;
    private final boolean hasEvaluatedExpressions;

    /**
     * Constructs empty annotation metadata.
     */
    @Internal
    protected DefaultAnnotationMetadata() {
        hasPropertyExpressions = false;
        hasEvaluatedExpressions = false;
    }

    /**
     * This constructor is designed to be used by compile time produced subclasses.
     *
     * @param declaredAnnotations     The directly declared annotations
     * @param declaredStereotypes     The directly declared stereotypes
     * @param allStereotypes          All stereotypes
     * @param allAnnotations          All annotations
     * @param annotationsByStereotype The annotations by stereotype
     */
    @Internal
    @UsedByGeneratedCode
    public DefaultAnnotationMetadata(
            @Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
            @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
            @Nullable Map<String, List<String>> annotationsByStereotype) {
        this(declaredAnnotations, declaredStereotypes, allStereotypes, allAnnotations, annotationsByStereotype, true);
    }

    /**
     * This constructor is designed to be used by compile time produced subclasses.
     *
     * @param declaredAnnotations     The directly declared annotations
     * @param declaredStereotypes     The directly declared stereotypes
     * @param allStereotypes          All stereotypes
     * @param allAnnotations          All annotations
     * @param annotationsByStereotype The annotations by stereotype
     * @param hasPropertyExpressions  Whether property expressions exist in the metadata
     */
    @Internal
    @UsedByGeneratedCode
    public DefaultAnnotationMetadata(
            @Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
            @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
            @Nullable Map<String, List<String>> annotationsByStereotype,
            boolean hasPropertyExpressions) {
        this(declaredAnnotations, declaredStereotypes, allStereotypes, allAnnotations, annotationsByStereotype, hasPropertyExpressions, false);
    }

    /**
     * This constructor is designed to be used by compile time produced subclasses.
     *
     * @param declaredAnnotations      The directly declared annotations
     * @param declaredStereotypes      The directly declared stereotypes
     * @param allStereotypes           All stereotypes
     * @param allAnnotations           All annotations
     * @param annotationsByStereotype  The annotations by stereotype
     * @param hasPropertyExpressions   Whether property expressions exist in the metadata
     * @param hasEvaluatedExpressions  Whether evaluated expressions exist in the metadata
     */
    @Internal
    @UsedByGeneratedCode
    public DefaultAnnotationMetadata(
            @Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
            @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
            @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
            @Nullable Map<String, List<String>> annotationsByStereotype,
            boolean hasPropertyExpressions,
            boolean hasEvaluatedExpressions) {
        this.declaredAnnotations = declaredAnnotations;
        this.declaredStereotypes = declaredStereotypes;
        this.allStereotypes = allStereotypes;
        this.allAnnotations = allAnnotations;
        this.annotationsByStereotype = annotationsByStereotype;
        this.hasPropertyExpressions = hasPropertyExpressions;
        this.hasEvaluatedExpressions = hasEvaluatedExpressions;
    }

    @NonNull
    @Override
    public AnnotationMetadata getDeclaredMetadata() {
        return new DefaultAnnotationMetadata(
                this.declaredAnnotations,
                this.declaredStereotypes,
                null,
                null,
                annotationsByStereotype,
                hasPropertyExpressions
        );
    }

    @Override
    public boolean hasPropertyExpressions() {
        return hasPropertyExpressions;
    }

    @Override
    public boolean hasEvaluatedExpressions() {
        return hasEvaluatedExpressions;
    }

    @NonNull
    @Override
    public Map<CharSequence, Object> getDefaultValues(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return AnnotationMetadataSupport.getDefaultValues(annotation);
    }

    @Override
    public boolean isPresent(@NonNull String annotation, @NonNull String member) {
        if (allAnnotations == null || StringUtils.isEmpty(annotation)) {
            return false;
        }
        Map<CharSequence, Object> values = allAnnotations.get(annotation);
        if (values != null) {
            return values.containsKey(member);
        }
        if (allStereotypes != null) {
            values = allStereotypes.get(annotation);
            if (values != null) {
                return values.containsKey(member);
            }
        }
        return false;
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, Class<E> enumType) {
        return enumValue(annotation, VALUE_MEMBER, enumType, null);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return enumValue(annotation, member, enumType, null);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return enumValue(annotation, VALUE_MEMBER, enumType);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return enumValue(annotation, member, enumType, null);
    }

    /**
     * Retrieve the class value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param enumType    The enum type
     * @param valueMapper The value mapper
     * @param <E>         The enum type
     * @return The class value
     */
    @Override
    @Internal
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.enumValue(member, enumType, valueMapper);
            }
            return Optional.empty();
        } else {
            return enumValue(annotation.getName(), member, enumType, valueMapper);
        }
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull String annotation, Class<E> enumType) {
        return enumValues(annotation, VALUE_MEMBER, enumType, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return enumValues(annotation, member, enumType, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return enumValues(annotation, VALUE_MEMBER, enumType, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return enumValues(annotation, member, enumType, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("enumType", enumType);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawValue(repeatableTypeName, member);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.enumValues(member, enumType);
            }
            return (E[]) Array.newInstance(enumType, 0);
        } else {
            Object v = getRawValue(annotation.getName(), member);
            return AnnotationValue.resolveEnumValues(enumType, v);
        }
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("enumType", enumType);
        Object v = getRawValue(annotation, member);
        return AnnotationValue.resolveEnumValues(enumType, v);
    }

    /**
     * Retrieve the class value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param enumType    The enum type
     * @param valueMapper The value mapper
     * @param <E>         The enum type
     * @return The class value
     */
    @Override
    @Internal
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        return enumValueOf(enumType, rawValue);
    }

    private <E extends Enum<E>> Optional<E> enumValueOf(Class<E> enumType, Object rawValue) {
        if (rawValue != null) {
            if (enumType.isInstance(rawValue)) {
                return Optional.of((E) rawValue);
            } else {
                try {
                    return Optional.of(Enum.valueOf(enumType, rawValue.toString()));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        Object rawSingleValue = getRawValue(annotation, member);
        //noinspection unchecked
        Class<T>[] classes = (Class<T>[]) AnnotationValue.resolveClassValues(rawSingleValue);
        if (classes != null) {
            return classes;
        }
        //noinspection unchecked
        return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, member, null);
            if (v instanceof AnnotationValue<?> annotationValue) {
                Class<?>[] classes = annotationValue.classValues(member);
                return (Class<T>[]) classes;
            }
            //noinspection unchecked
            return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
        } else {
            return classValues(annotation.getName(), member);
        }
    }

    @NonNull
    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return classValue(annotation, member, null);
    }

    /**
     * Retrieve the class value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, member, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return (Optional) (annotationValue.classValue(member, valueMapper));
            }
            return Optional.empty();
        } else {
            return classValue(annotation.getName(), member, valueMapper);
        }
    }

    @NonNull
    @Override
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        return classValue(annotation, member, null);
    }

    /**
     * Retrieve the class value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    @Override
    @Internal
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof AnnotationClassValue annotationClassValue) {
            return annotationClassValue.getType();
        } else if (rawValue instanceof Class class1) {
            return Optional.of(class1);
        } else if (rawValue != null) {
            return ConversionService.SHARED.convert(rawValue, Class.class);
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return intValue(annotation, member, null);
    }

    @NonNull
    @Override
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return intValue(annotation, member, null);
    }

    /**
     * Retrieve the int value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Override
    @Internal
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.intValue(member, valueMapper);
            }
            return OptionalInt.empty();
        } else {
            return intValue(annotation.getName(), member, valueMapper);
        }
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return booleanValue(annotation, member, null);
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return booleanValue(annotation, member, null);
    }

    /**
     * Retrieve the boolean value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, null);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.booleanValue(member, valueMapper);
            }
            return Optional.empty();
        } else {
            return booleanValue(annotation.getName(), member, valueMapper);
        }
    }

    /**
     * Retrieve the boolean value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @Override
    @NonNull
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof Boolean aBoolean) {
            return Optional.of(aBoolean);
        } else if (rawValue != null) {
            return Optional.of(StringUtils.isTrue(rawValue.toString()));
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return longValue(annotation, member, null);
    }

    @NonNull
    @Override
    public OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return longValue(annotation, member, null);
    }

    /**
     * Retrieve the long value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Override
    @Internal
    public OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.longValue(member, valueMapper);
            }
            return OptionalLong.empty();
        } else {
            return longValue(annotation.getName(), member, valueMapper);
        }
    }

    /**
     * Retrieve the long value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Override
    @NonNull
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof Number number) {
            return OptionalLong.of(number.longValue());
        } else if (rawValue instanceof CharSequence) {
            final String str = rawValue.toString();
            if (StringUtils.isNotEmpty(str)) {
                try {
                    final long i = Long.parseLong(str);
                    return OptionalLong.of(i);
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Invalid value [" + str + "] of [" + member + "] of annotation [" + annotation + "]: " + e.getMessage(), e);
                }
            }

        }
        return OptionalLong.empty();
    }

    /**
     * Retrieve the int value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Override
    @NonNull
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof Number number) {
            return OptionalInt.of(number.intValue());
        } else if (rawValue instanceof CharSequence) {
            final String str = rawValue.toString();
            if (StringUtils.isNotEmpty(str)) {
                try {
                    final int i = Integer.parseInt(str);
                    return OptionalInt.of(i);
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Invalid value [" + str + "] of [" + member + "] of annotation [" + annotation + "]: " + e.getMessage(), e);
                }
            }

        }
        return OptionalInt.empty();
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return stringValue(annotation, member, null);
    }

    /**
     * Retrieve the string value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.stringValue(member, valueMapper);
            }
            return Optional.empty();
        } else {
            return stringValue(annotation.getName(), member, valueMapper);
        }
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return stringValues(annotation.getName(), member, null);
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull String annotation, @NonNull String member) {
        return stringValues(annotation, member, null);
    }

    /**
     * Retrieve the string value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Override
    @NonNull
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawValue(repeatableTypeName, member);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.stringValues(member, valueMapper);
            }
            return StringUtils.EMPTY_STRING_ARRAY;
        } else {
            Object v = getRawValue(annotation.getName(), member);
            String[] strings = AnnotationValue.resolveStringValues(v, valueMapper);
            return strings != null ? strings : StringUtils.EMPTY_STRING_ARRAY;
        }
    }

    /**
     * Retrieve the string value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Override
    @NonNull
    public String[] stringValues(@NonNull String annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Object v = getRawValue(annotation, member);
        String[] strings = AnnotationValue.resolveStringValues(v, valueMapper);
        return strings != null ? strings : StringUtils.EMPTY_STRING_ARRAY;
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        return stringValue(annotation, member, null);
    }

    /**
     * Retrieve the string value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The string value
     */
    @Override
    @NonNull
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof String s) {
            // Performance optimization to check for the actual class first to avoid the type-check polution
            return Optional.of(s);
        } else if (rawValue instanceof CharSequence) {
            return Optional.of(rawValue.toString());
        } else if (rawValue instanceof Class<?> aClass) {
            String name = aClass.getName();
            return Optional.of(name);
        } else if (rawValue != null) {
            return Optional.of(rawValue.toString());
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return isTrue(annotation, member, null);
    }

    /**
     * Retrieve the boolean value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @Override
    public boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.isTrue(member, valueMapper);
            }
            return false;
        } else {
            return isTrue(annotation.getName(), member, valueMapper);
        }
    }

    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return isTrue(annotation, member, null);
    }

    /**
     * Retrieve the boolean value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof Boolean aBoolean) {
            return aBoolean;
        } else if (rawValue != null) {
            String booleanString = rawValue.toString().toLowerCase(Locale.ENGLISH);
            return StringUtils.isTrue(booleanString);
        }
        return false;
    }

    @Override
    public boolean isFalse(@NonNull String annotation, @NonNull String member) {
        return !isTrue(annotation, member);
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        return doubleValue(annotation, member, null);
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return doubleValue(annotation, member, null);
    }

    /**
     * Retrieve the double value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Override
    @Internal
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            Object v = getRawSingleValue(repeatableTypeName, VALUE_MEMBER, valueMapper);
            if (v instanceof AnnotationValue<?> annotationValue) {
                return annotationValue.doubleValue(member, valueMapper);
            }
            return OptionalDouble.empty();
        } else {
            return doubleValue(annotation.getName(), member);
        }
    }

    /**
     * Retrieve the double value and optionally map its value.
     *
     * @param annotation  The annotation
     * @param member      The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Override
    @NonNull
    @Internal
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        Object rawValue = getRawSingleValue(annotation, member, valueMapper);
        if (rawValue instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        } else if (rawValue instanceof CharSequence) {
            final String str = rawValue.toString();
            if (StringUtils.isNotEmpty(str)) {
                try {
                    final double i = Double.parseDouble(str);
                    return OptionalDouble.of(i);
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Invalid value [" + str + "] of member [" + member + "] of annotation [" + annotation + "]: " + e.getMessage(), e);
                }
            }

        }
        return OptionalDouble.empty();
    }

    @Override
    public @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation.getName());
        if (repeatableTypeName != null) {
            List<? extends AnnotationValue<? extends Annotation>> values = getAnnotationValuesByType(annotation);
            if (!values.isEmpty()) {
                return values.iterator().next().get(member, requiredType);
            } else {
                return Optional.empty();
            }
        } else {
            return getValue(annotation.getName(), member, requiredType);
        }
    }

    @Override
    public @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return getValue(annotation, member, requiredType, null);
    }

    /**
     * Resolves the given value performing type conversion as necessary.
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param valueMapper  The value mapper
     * @param <T>          The generic type
     * @return The resolved value
     */
    @Override
    @NonNull
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);
        Optional<T> resolved = Optional.empty();
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                Object rawValue = values.get(member);
                if (rawValue != null) {
                    if (valueMapper != null) {
                        rawValue = valueMapper.apply(rawValue);
                    }
                    resolved = ConversionService.SHARED.convert(rawValue, requiredType);
                }
            } else if (allStereotypes != null) {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    Object rawValue = values.get(member);
                    if (rawValue != null) {
                        if (valueMapper != null) {
                            rawValue = valueMapper.apply(rawValue);
                        }
                        resolved = ConversionService.SHARED.convert(rawValue, requiredType);
                    }
                }
            }
        }
        if (resolved.isEmpty() && hasStereotype(annotation)) {
            return getDefaultValue(annotation, member, requiredType);
        }
        return resolved;
    }

    @Override
    public @NonNull <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        Map<CharSequence, Object> defaultValues = getDefaultValues(annotation);
        final Object v = defaultValues.get(member);
        if (v == null) {
            return Optional.empty();
        }
        if (requiredType.isInstance(v)) {
            return (Optional<T>) Optional.of(v);
        }
        return ConversionService.SHARED.convert(v, requiredType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nullable Class<T> annotationType) {
        if (annotationType == null) {
            return List.of();
        }
        final String annotationTypeName = annotationType.getName();
        List<AnnotationValue<T>> results = annotationValuesByType.get(annotationTypeName);
        if (results == null) {
            results = resolveAnnotationValuesByType(annotationType, allAnnotations, allStereotypes);
            if (results != null) {
                return results;
            } else if (allAnnotations != null) {
                final Map<CharSequence, Object> values = allAnnotations.get(annotationTypeName);
                if (values != null) {
                    results = List.of(newAnnotationValue(annotationTypeName, values));
                }
            }
            if (results == null) {
                results = List.of();
            }
            annotationValuesByType.put(annotationTypeName, results);
        }
        return results;
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        if (annotationType == null) {
            return List.of();
        }
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotationType);
        if (repeatableTypeName == null) {
            return List.of();
        }
        List<AnnotationValue<T>> results = resolveRepeatableAnnotations(repeatableTypeName, allAnnotations, allStereotypes);
        if (results != null) {
            return results;
        }
        if (allAnnotations != null) {
            final Map<CharSequence, Object> values = allAnnotations.get(annotationType);
            if (values != null) {
                results = List.of(newAnnotationValue(annotationType, values));
            }
        }
        if (results == null) {
            results = List.of();
        }
        annotationValuesByType.put(annotationType, results);
        return List.of();
    }

    @NonNull
    protected <T extends Annotation> AnnotationValue<T> newAnnotationValue(String annotationType, Map<CharSequence, Object> values) {
        return new AnnotationValue<>(annotationType, values, AnnotationMetadataSupport.getDefaultValuesOrNull(annotationType));
    }

    @NonNull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
        if (annotationType == null) {
            return List.of();
        }
        List<AnnotationValue<T>> results = resolveAnnotationValuesByType(annotationType, declaredAnnotations, declaredStereotypes);
        if (results != null) {
            return results;
        }
        return List.of();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(String annotationType) {
        if (annotationType == null) {
            return List.of();
        }
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotationType);
        List<AnnotationValue<T>> results = resolveRepeatableAnnotations(repeatableTypeName, declaredAnnotations, declaredStereotypes);
        if (results != null) {
            return results;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T[] synthesizeAnnotationsByType(@NonNull Class<T> annotationClass) {
        if (annotationClass == null) {
            return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
        }
        return getAnnotationValuesByType(annotationClass).stream()
                .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries))
                .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @Override
    public <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@NonNull Class<T> annotationClass) {
        if (annotationClass == null) {
            return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
        }
        return getAnnotationValuesByType(annotationClass).stream()
                .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries))
                .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @Override
    public boolean isEmpty() {
        return allAnnotations == null || allAnnotations.isEmpty();
    }

    @Override
    public boolean hasDeclaredAnnotation(String annotation) {
        return declaredAnnotations != null && StringUtils.isNotEmpty(annotation) && declaredAnnotations.containsKey(annotation);
    }

    @Override
    public boolean hasAnnotation(String annotation) {
        return hasDeclaredAnnotation(annotation) || (allAnnotations != null && StringUtils.isNotEmpty(annotation) && allAnnotations.containsKey(annotation));
    }

    @Override
    public boolean hasStereotype(String annotation) {
        return hasAnnotation(annotation) || (allStereotypes != null && StringUtils.isNotEmpty(annotation) && allStereotypes.containsKey(annotation));
    }

    @Override
    public boolean hasStereotype(Class<? extends Annotation> annotation) {
        if (annotation != null) {
            String repeatableAnnotationContainer = findRepeatableAnnotationContainerInternal(annotation.getName());
            if (repeatableAnnotationContainer != null) {
                return hasStereotype(repeatableAnnotationContainer);
            }
            return hasStereotype(annotation.getName());
        }
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(String annotation) {
        return hasDeclaredAnnotation(annotation) || (declaredStereotypes != null && StringUtils.isNotEmpty(annotation) && declaredStereotypes.containsKey(annotation));
    }

    @Override
    public boolean hasDeclaredAnnotation(Class<? extends Annotation> annotation) {
        if (annotation != null) {
            String repeatableAnnotationContainer = findRepeatableAnnotationContainerInternal(annotation.getName());
            if (repeatableAnnotationContainer != null) {
                return hasDeclaredAnnotation(repeatableAnnotationContainer);
            }
            return hasDeclaredAnnotation(annotation.getName());
        }
        return false;
    }

    @NonNull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        if (stereotype == null) {
            return Optional.empty();
        }
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (CollectionUtils.isNotEmpty(annotations)) {
                return getAnnotationType(annotations.get(0));
            }
        }
        if (allAnnotations != null && allAnnotations.containsKey(stereotype)) {
            return getAnnotationType(stereotype);
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return getAnnotationType(stereotype);
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<String> getAnnotationNameByStereotype(@Nullable String stereotype) {
        if (stereotype == null) {
            return Optional.empty();
        }
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (CollectionUtils.isNotEmpty(annotations)) {
                return Optional.of(annotations.get(0));
            }
        }
        if (allAnnotations != null && allAnnotations.containsKey(stereotype)) {
            return Optional.of(stereotype);
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return Optional.of(stereotype);
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        if (stereotype == null) {
            return List.of();
        }
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                return Collections.unmodifiableList(annotations);
            }
        }
        if (allAnnotations != null && allAnnotations.containsKey(stereotype)) {
            return List.of(stereotype);
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return List.of(stereotype);
        }
        return List.of();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(String stereotype) {
        if (stereotype == null) {
            return List.of();
        }
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                List<AnnotationValue<T>> result = new ArrayList<>(annotations.size());
                for (String annotation : annotations) {
                    String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotation);
                    if (repeatableTypeName != null) {
                        List<AnnotationValue<T>> results =
                                resolveRepeatableAnnotations(repeatableTypeName,
                                        allAnnotations,
                                        allStereotypes
                                );
                        if (results != null) {
                            result.addAll(results);
                        }
                    } else {
                        result.add(getAnnotation(annotation));
                    }
                }
                return Collections.unmodifiableList(result);
            }
        }
        if (allAnnotations != null) {
            return getAnnotationValuesByName(stereotype);
        }
        if (declaredAnnotations != null) {
            return getDeclaredAnnotationValuesByName(stereotype);
        }
        return List.of();
    }

    @NonNull
    @Override
    public Set<String> getAnnotationNames() {
        if (allAnnotations != null) {
            return Collections.unmodifiableSet(allAnnotations.keySet());
        }
        return Set.of();
    }

    @NonNull
    @Override
    public Set<String> getStereotypeAnnotationNames() {
        if (allStereotypes != null) {
            return Collections.unmodifiableSet(allStereotypes.keySet());
        }
        return Set.of();
    }

    @NonNull
    @Override
    public Set<String> getDeclaredStereotypeAnnotationNames() {
        if (declaredStereotypes != null) {
            return Collections.unmodifiableSet(declaredStereotypes.keySet());
        }
        return Set.of();
    }

    @NonNull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        if (declaredAnnotations != null) {
            return Collections.unmodifiableSet(declaredAnnotations.keySet());
        }
        return Set.of();
    }

    @NonNull
    @Override
    public List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        if (stereotype == null) {
            return List.of();
        }
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                annotations = new ArrayList<>(annotations);
                if (declaredAnnotations != null) {
                    annotations.removeIf(s -> !declaredAnnotations.containsKey(s));
                    return Collections.unmodifiableList(annotations);
                } else {
                    // no declared
                    return List.of();
                }
            }
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return List.of(stereotype);
        }
        return List.of();
    }

    @NonNull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        return AnnotationMetadataSupport.getAnnotationType(name);
    }

    @NonNull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name, @NonNull ClassLoader classLoader) {
        return AnnotationMetadataSupport.getAnnotationType(name, classLoader);
    }

    @SuppressWarnings("Duplicates")
    @NonNull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        if (allAnnotations == null || StringUtils.isEmpty(annotation)) {
            return Optional.empty();
        }
        Map<CharSequence, Object> values = allAnnotations.get(annotation);
        if (values != null) {
            return Optional.of(newAnnotationValue(annotation, values));
        }
        if (allStereotypes != null) {
            values = allStereotypes.get(annotation);
            if (values != null) {
                return Optional.of(newAnnotationValue(annotation, values));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("Duplicates")
    @NonNull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        if (declaredAnnotations == null || StringUtils.isEmpty(annotation)) {
            return Optional.empty();
        }
        Map<CharSequence, Object> values = declaredAnnotations.get(annotation);
        if (values != null) {
            return Optional.of(newAnnotationValue(annotation, values));
        }
        if (declaredStereotypes != null) {
            values = declaredStereotypes.get(annotation);
            if (values != null) {
                return Optional.of(newAnnotationValue(annotation, values));
            }
        }
        return Optional.empty();
    }

    @Override
    public @NonNull <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("valueType", valueType);
        if (allAnnotations == null || StringUtils.isEmpty(annotation)) {
            return OptionalValues.empty();
        }
        Map<CharSequence, Object> values = allAnnotations.get(annotation);
        if (values != null) {
            return OptionalValues.of(valueType, values);
        }
        if (allStereotypes != null) {
            values = allStereotypes.get(annotation);
            if (values != null) {
                return OptionalValues.of(valueType, values);
            }
        }
        return OptionalValues.empty();
    }

    @NonNull
    @Override
    public Map<CharSequence, Object> getValues(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        if (allAnnotations == null || StringUtils.isEmpty(annotation)) {
            return Collections.emptyMap();
        }
        Map<CharSequence, Object> values = allAnnotations.get(annotation);
        if (values != null) {
            return Collections.unmodifiableMap(values);
        }
        if (allStereotypes != null) {
            values = allStereotypes.get(annotation);
            if (values != null) {
                return Collections.unmodifiableMap(values);
            }
        }
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);
        // Note this method should never reference the "annotationDefaultValues" field, which is used only at compile time
        Map<CharSequence, Object> defaultValues = getDefaultValues(annotation);
        Object value = defaultValues.get(member);
        if (value != null) {
            return ConversionService.SHARED.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public boolean isRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return isRepeatableAnnotation(annotation.getName());
    }

    @Override
    public boolean isRepeatableAnnotation(String annotation) {
        return findRepeatableAnnotationContainerInternal(annotation) != null;
    }

    @Override
    public Optional<String> findRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return findRepeatableAnnotation(annotation.getName());
    }

    @Override
    public Optional<String> findRepeatableAnnotation(String annotation) {
        return Optional.ofNullable(findRepeatableAnnotationContainerInternal(annotation));
    }

    @Override
    public AnnotationMetadata copyAnnotationMetadata() {
        return clone();
    }

    @Override
    public DefaultAnnotationMetadata clone() {
        DefaultAnnotationMetadata cloned = new DefaultAnnotationMetadata(
                declaredAnnotations != null ? cloneMapOfMapValue(declaredAnnotations) : null,
                declaredStereotypes != null ? cloneMapOfMapValue(declaredStereotypes) : null,
                allStereotypes != null ? cloneMapOfMapValue(allStereotypes) : null,
                allAnnotations != null ? cloneMapOfMapValue(allAnnotations) : null,
                annotationsByStereotype != null ? cloneMapOfListValue(annotationsByStereotype) : null,
                hasPropertyExpressions
        );
        return cloned;
    }

    protected final <X, Y, K> Map<K, Map<X, Y>> cloneMapOfMapValue(Map<K, Map<X, Y>> toClone) {
        return toClone.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cloneMap(e.getValue())))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    protected final <K, V> Map<K, List<V>> cloneMapOfListValue(Map<K, List<V>> toClone) {
        return toClone.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), new ArrayList<>(e.getValue())))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    protected final <K, V> Map<K, V> cloneMap(Map<K, V> map) {
        Map<K, V> newMap;
        if (map instanceof LinkedHashMap<K, V> linkedHashMap) {
            newMap = (Map<K, V>) linkedHashMap.clone();
        } else {
            newMap = new LinkedHashMap<>(map);
        }
        for (Map.Entry<K, V> entry : newMap.entrySet()) {
            if (entry.getValue() instanceof Set) {
                LinkedHashSet<Object> newValue = new LinkedHashSet<>((Collection) entry.getValue());
                entry.setValue((V) newValue);
            }
        }
        return new HashMap<>(newMap);
    }

    /**
     * Registers annotation default values. Used by generated byte code. DO NOT REMOVE.
     *
     * @param annotation    The annotation name
     * @param defaultValues The default values
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    public static void registerAnnotationDefaults(String annotation, Map<CharSequence, Object> defaultValues) {
        AnnotationMetadataSupport.registerDefaultValues(annotation, defaultValues);
    }

    /**
     * Registers annotation default values. Used by generated byte code. DO NOT REMOVE.
     *
     * @param annotation    The annotation name
     * @param defaultValues The default values
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    public static void registerAnnotationDefaults(AnnotationClassValue<?> annotation, Map<CharSequence, Object> defaultValues) {
        AnnotationMetadataSupport.registerDefaultValues(annotation, defaultValues);
    }

    /**
     * Registers annotation default values. Used by generated byte code. DO NOT REMOVE.
     *
     * @param annotation The annotation
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    public static void registerAnnotationType(AnnotationClassValue<?> annotation) {
        AnnotationMetadataSupport.registerAnnotationType(annotation);
    }

    /**
     * Registers repeatable annotations. Annotation container -> annotations item. Used by generated byte code. DO NOT REMOVE.
     *
     * @param repeatableAnnotations The annotation
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    public static void registerRepeatableAnnotations(Map<String, String> repeatableAnnotations) {
        AnnotationMetadataSupport.registerRepeatableAnnotations(repeatableAnnotations);
    }

    /**
     * Dump the values.
     */
    @SuppressWarnings("unused")
    @Internal
    void dump() {
        System.out.println("declaredAnnotations = " + declaredAnnotations);
        System.out.println("declaredStereotypes = " + declaredStereotypes);
        System.out.println("allAnnotations = " + allAnnotations);
        System.out.println("allStereotypes = " + allStereotypes);
        System.out.println("annotationsByStereotype = " + annotationsByStereotype);
    }

    private <T extends Annotation> List<io.micronaut.core.annotation.AnnotationValue<T>> resolveAnnotationValuesByType(Class<T> annotationType, Map<String, Map<CharSequence, Object>> sourceAnnotations, Map<String, Map<CharSequence, Object>> sourceStereotypes) {
        String repeatableTypeName = findRepeatableAnnotationContainerInternal(annotationType.getName());
        if (repeatableTypeName != null) {
            return resolveRepeatableAnnotations(repeatableTypeName,
                    sourceStereotypes,
                    sourceAnnotations
            );
        }
        return null;
    }

    @Nullable
    private <T extends Annotation> List<AnnotationValue<T>> resolveRepeatableAnnotations(String repeatableTypeName,
                                                                                         Map<String, Map<CharSequence, Object>> sourceStereotypes,
                                                                                         Map<String, Map<CharSequence, Object>> sourceAnnotations) {
        if (!hasStereotype(repeatableTypeName)) {
            return null;
        }
        List<AnnotationValue<T>> results = null;
        if (sourceAnnotations != null) {
            Map<CharSequence, Object> values = sourceAnnotations.get(repeatableTypeName);
            results = collectResult(results, values);
        }
        if (sourceStereotypes != null) {
            Map<CharSequence, Object> values = sourceStereotypes.get(repeatableTypeName);
            results = collectResult(results, values);
        }
        return results == null ? List.of() : results;
    }

    @Nullable
    private Object getRawSingleValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        Object rawValue = getRawValue(annotation, member);
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
        if (valueMapper != null && rawValue instanceof CharSequence) {
            return valueMapper.apply(rawValue);
        } else {
            return rawValue;
        }
    }

    @Nullable
    private Object getRawValue(@NonNull String annotation, @NonNull String member) {
        if (allAnnotations == null || StringUtils.isEmpty(annotation)) {
            return null;
        }
        Map<CharSequence, Object> values = allAnnotations.get(annotation);
        if (values != null) {
            return values.get(member);
        }
        if (allStereotypes != null) {
            values = allStereotypes.get(annotation);
            if (values != null) {
                return values.get(member);
            }
        }
        return null;
    }

    /**
     * Find annotation's repeatable container.
     * @param annotation The annotation
     * @return the repeatable container or null
     */
    @Nullable
    protected String findRepeatableAnnotationContainerInternal(@NonNull String annotation) {
        return AnnotationMetadataSupport.getRepeatableAnnotation(annotation);
    }

    private <T extends Annotation> List<AnnotationValue<T>> collectResult(List<AnnotationValue<T>> results, Map<CharSequence, Object> values) {
        if (values != null) {
            Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
            if (v instanceof AnnotationValue<?>[] avs) {
                List<AnnotationValue<T>> result = (List) Arrays.asList(avs);
                if (results == null) {
                    return result;
                } else {
                    return CollectionUtils.concat(results, result);
                }
            } else if (v instanceof Collection<?> c) {
                List<AnnotationValue<T>> result = new ArrayList<>(c.size());
                for (Object o : c) {
                    if (o instanceof io.micronaut.core.annotation.AnnotationValue<?> av) {
                        result.add((AnnotationValue<T>) av);
                    }
                }
                if (results == null) {
                    return result;
                } else {
                    return CollectionUtils.concat(results, result);
                }
            }
        }
        return results;
    }
}
