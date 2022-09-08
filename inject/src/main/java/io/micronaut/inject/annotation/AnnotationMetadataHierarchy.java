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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Used to represent an annotation metadata hierarchy. The first {@link AnnotationMetadata} instance passed
 * to the constructor represents the annotation metadata that is declared, hence methods like {@link #hasDeclaredAnnotation(String)} will return true for the last annotation metadata passed in the hierarchy.
 *
 * <p>This class is used to internally optimize memory usage and compilation time for classes that declare
 * AOP advice at the type level and where the classes methods typically don't include any annotations and therefore
 * would be wasteful to generate additional annotation metadata classes.</p>
 *
 * @author graemerocher
 * @since 1.3.0
 */
public final class AnnotationMetadataHierarchy implements AnnotationMetadata, EnvironmentAnnotationMetadata, Iterable<AnnotationMetadata> {
    /**
     * Constant to represent an empty hierarchy.
     */
    public static final AnnotationMetadata[] EMPTY_HIERARCHY = {AnnotationMetadata.EMPTY_METADATA, AnnotationMetadata.EMPTY_METADATA};

    private final AnnotationMetadata[] hierarchy;
    private final boolean delegateDeclaredToAllElements;

    /**
     * Default constructor.
     *
     * @param hierarchy The annotation hierarchy
     */
    public AnnotationMetadataHierarchy(AnnotationMetadata... hierarchy) {
        this(false, hierarchy);
    }

    /**
     * Default constructor.
     *
     * @param hierarchy                     The annotation hierarchy
     * @param delegateDeclaredToAllElements The delegate declared to all elements
     */
    @Internal
    public AnnotationMetadataHierarchy(boolean delegateDeclaredToAllElements, AnnotationMetadata... hierarchy) {
        this.delegateDeclaredToAllElements = delegateDeclaredToAllElements;
        if (ArrayUtils.isNotEmpty(hierarchy)) {
            ArrayUtils.reverse(hierarchy);
            this.hierarchy = hierarchy;
        } else {
            this.hierarchy = EMPTY_HIERARCHY;
        }
    }

    /**
     * Copy constructor.
     *
     * @param existing Existing
     * @param newChild new child
     */
    private AnnotationMetadataHierarchy(AnnotationMetadata[] existing, AnnotationMetadata newChild) {
        hierarchy = new AnnotationMetadata[existing.length];
        System.arraycopy(existing, 0, hierarchy, 0, existing.length);
        hierarchy[0] = newChild;
        delegateDeclaredToAllElements = false;
    }

    @Override
    public boolean hasPropertyExpressions() {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasPropertyExpressions()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        return getAnnotationType((metadata) -> metadata.getAnnotationType(name));
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name, @NonNull ClassLoader classLoader) {
        return getAnnotationType((metadata) -> metadata.getAnnotationType(name, classLoader));
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    @NonNull
    @Override
    public AnnotationMetadata getDeclaredMetadata() {
        return hierarchy[0];
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    @NonNull
    public AnnotationMetadata getRootMetadata() {
        return hierarchy[hierarchy.length - 1];
    }

    /**
     * Create a new hierarchy instance from this metadata using this metadata's parents.
     *
     * @param child The child annotation metadata
     * @return A new sibling
     */
    @NonNull
    public AnnotationMetadata createSibling(@NonNull AnnotationMetadata child) {
        if (hierarchy.length > 1) {
            return new AnnotationMetadataHierarchy(hierarchy, child);
        } else {
            return child;
        }
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass, @NonNull String sourceAnnotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final T a = annotationMetadata.synthesize(annotationClass, sourceAnnotation);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    @Override
    public Annotation[] synthesizeAll() {
        return Stream.of(hierarchy).flatMap(am -> Arrays.stream(am.synthesizeAll())).toArray(Annotation[]::new);
    }

    @Override
    public Annotation[] synthesizeDeclared() {
        return Stream.of(hierarchy).flatMap(am -> Arrays.stream(am.synthesizeDeclared())).toArray(Annotation[]::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T[] synthesizeAnnotationsByType(Class<T> annotationClass) {
        if (annotationClass == null) {
            return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
        }
        return Stream.of(hierarchy)
            .flatMap(am -> am.getAnnotationValuesByType(annotationClass).stream())
            .distinct()
            .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries))
            .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @SuppressWarnings("unchecked")
    public <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(Class<T> annotationClass) {
        if (annotationClass == null) {
            return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
        }
        return Stream.of(hierarchy)
            .flatMap(am -> am.getAnnotationValuesByType(annotationClass).stream())
            .distinct()
            .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries))
            .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass, @NonNull String sourceAnnotation) {
        return hierarchy[0].synthesize(annotationClass, sourceAnnotation);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final T a = annotationMetadata.synthesize(annotationClass);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        if (delegateDeclaredToAllElements) {
            return merge().synthesizeDeclared(annotationClass);
        }
        return hierarchy[0].synthesize(annotationClass);
    }

    @NonNull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        AnnotationValue<T> existing = null;
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            existing = mergeValue(annotation, existing, annotationMetadata.getAnnotation(annotation));
        }
        return Optional.ofNullable(existing);
    }

    @Nullable
    private <T extends Annotation> AnnotationValue<T> mergeValue(@NonNull String annotation,
                                                                 @Nullable AnnotationValue<T> existingValue,
                                                                 @Nullable AnnotationValue<T> newValud) {
        if (newValud == null) {
            return existingValue;
        }
        if (existingValue == null) {
            return newValud;
        }
        final Map<CharSequence, Object> values = newValud.getValues();
        final Map<CharSequence, Object> existing = existingValue.getValues();
        Map<CharSequence, Object> newValues = new LinkedHashMap<>(values.size() + existing.size());
        newValues.putAll(existing);
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            newValues.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return new AnnotationValue<>(annotation, newValues, AnnotationMetadataSupport.getDefaultValues(annotation));
    }

    @NonNull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        if (delegateDeclaredToAllElements) {
            AnnotationValue<T> existing = null;
            for (AnnotationMetadata annotationMetadata : hierarchy) {
                existing = mergeValue(annotation, existing, annotationMetadata.getDeclaredAnnotation(annotation));
            }
            return Optional.ofNullable(existing);
        }
        return hierarchy[0].findDeclaredAnnotation(annotation);
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return stringValues(annotation.getName(), member);
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull String annotation, @NonNull String member) {
        String[] values = hierarchy[0].stringValues(annotation, member);
        for (int i = 1; i < hierarchy.length; i++) {
            AnnotationMetadata annotationMetadata = hierarchy[i];
            final String[] moreValues = annotationMetadata.stringValues(annotation, member);
            if (ArrayUtils.isNotEmpty(moreValues)) {
                values = ArrayUtils.concat(values, moreValues);
            }
        }
        return values;
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o = annotationMetadata.booleanValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata am : hierarchy) {
            if (am.isTrue(annotation, member)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o = annotationMetadata.longValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o = annotationMetadata.stringValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o = annotationMetadata.intValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o = annotationMetadata.enumValue(annotation, member, enumType);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        List<Class<T>> list = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            list.addAll(Arrays.asList(am.classValues(annotation, member)));
        }
        return ArrayUtils.toArray(list, Class[]::new);
    }

    @Override
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o = annotationMetadata.classValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        List<String> list = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            list.addAll(am.getAnnotationNamesByStereotype(stereotype));
        }
        return list;
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(String stereotype) {
        List<AnnotationValue<T>> list = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            list.addAll(am.getAnnotationValuesByStereotype(stereotype));
        }
        return list;
    }

    @NonNull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        if (delegateDeclaredToAllElements) {
            Set<String> set = new HashSet<>();
            for (AnnotationMetadata am : hierarchy) {
                set.addAll(am.getDeclaredAnnotationNames());
            }
            return set;
        }
        return hierarchy[0].getDeclaredAnnotationNames();
    }

    @NonNull
    @Override
    public Set<String> getAnnotationNames() {
        Set<String> set = new HashSet<>();
        for (AnnotationMetadata am : hierarchy) {
            set.addAll(am.getAnnotationNames());
        }
        return set;
    }

    @NonNull
    @Override
    public <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalValues<T> values = annotationMetadata.getValues(annotation, valueType);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return OptionalValues.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> defaultValue = annotationMetadata.getDefaultValue(annotation, member, requiredType);
            if (defaultValue.isPresent()) {
                return defaultValue;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@NonNull Class<T> annotationType) {
        List<AnnotationValue<T>> list = new ArrayList<>(10);
        Set<AnnotationValue<T>> uniqueValues = new HashSet<>(10);
        for (AnnotationMetadata am : hierarchy) {
            for (AnnotationValue<T> tAnnotationValue : am.getAnnotationValuesByType(annotationType)) {
                if (uniqueValues.add(tAnnotationValue)) {
                    list.add(tAnnotationValue);
                }
            }
        }
        return list;
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        if (annotationType == null) {
            return Collections.emptyList();
        }
        return mergeAnnotationValues(annotationType, AnnotationMetadata::getAnnotationValuesByName);
    }

    @NonNull
    private <T extends Annotation, V> List<AnnotationValue<T>> mergeAnnotationValues(V annotationType,
                                                                                     BiFunction<AnnotationMetadata, V, List<AnnotationValue<T>>> fn) {
        List<AnnotationValue<T>> list = new ArrayList<>(10);
        Set<AnnotationValue<T>> uniqueValues = new HashSet<>(10);
        for (AnnotationMetadata am : hierarchy) {
            for (AnnotationValue<T> tAnnotationValue : fn.apply(am, annotationType)) {
                if (uniqueValues.add(tAnnotationValue)) {
                    list.add(tAnnotationValue);
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    @NonNull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
        if (delegateDeclaredToAllElements) {
            return mergeAnnotationValues(annotationType, AnnotationMetadata::getDeclaredAnnotationValuesByType);
        }
        return hierarchy[0].getDeclaredAnnotationValuesByType(annotationType);
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(String annotationType) {
        if (delegateDeclaredToAllElements) {
            return mergeAnnotationValues(annotationType, AnnotationMetadata::getDeclaredAnnotationValuesByName);
        }
        return hierarchy[0].getDeclaredAnnotationValuesByName(annotationType);
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        if (delegateDeclaredToAllElements) {
            for (AnnotationMetadata annotationMetadata : hierarchy) {
                if (annotationMetadata.hasDeclaredAnnotation(annotation)) {
                    return true;
                }
            }
            return false;
        }
        return hierarchy[0].hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        if (delegateDeclaredToAllElements) {
            for (AnnotationMetadata annotationMetadata : hierarchy) {
                if (annotationMetadata.hasDeclaredStereotype(annotation)) {
                    return true;
                }
            }
            return false;
        }
        return hierarchy[0].hasDeclaredStereotype(annotation);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType) {
        return enumValue(annotation, member, enumType, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType) {
        return enumValues(annotation, member, enumType, null);
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation, String member) {
        return intValue(annotation, member, null);
    }

    @Override
    public boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return !booleanValue(annotation, member, null).orElse(false);
    }

    @NonNull
    @Override
    public Map<String, Object> getDefaultValues(@NonNull String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Map<String, Object> defaultValues = annotationMetadata.getDefaultValues(annotation);
            if (!defaultValues.isEmpty()) {
                return defaultValues;
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).enumValue(annotation, member, enumType, valueMapper);
            } else {
                o = annotationMetadata.enumValue(annotation, member, enumType);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).enumValue(annotation, member, enumType, valueMapper);
            } else {
                o = annotationMetadata.enumValue(annotation, member, enumType);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        E[] values = hierarchy[0].enumValues(annotation, member, enumType);
        for (int i = 1; i < hierarchy.length; i++) {
            AnnotationMetadata annotationMetadata = hierarchy[i];

            final E[] moreValues = annotationMetadata.enumValues(annotation, member, enumType);
            if (ArrayUtils.isNotEmpty(moreValues)) {
                values = ArrayUtils.concat(values, moreValues);
            }
        }
        return values;
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        E[] values = hierarchy[0].enumValues(annotation, member, enumType);
        for (int i = 1; i < hierarchy.length; i++) {
            AnnotationMetadata annotationMetadata = hierarchy[i];

            final E[] moreValues = annotationMetadata.enumValues(annotation, member, enumType);
            if (ArrayUtils.isNotEmpty(moreValues)) {
                values = ArrayUtils.concat(values, moreValues);
            }
        }
        return values;
    }

    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).classValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.classValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).classValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.classValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).intValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.intValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).booleanValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.booleanValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).booleanValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.booleanValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).longValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.longValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @NonNull
    @Override
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).longValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.longValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @NonNull
    @Override
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).intValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.intValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public OptionalLong longValue(Class<? extends Annotation> annotation, String member) {
        return longValue(annotation, member, null);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return enumValues(annotation, member, enumType, null);
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation, String member) {
        List<Class<T>> list = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            list.addAll(Arrays.asList(am.classValues(annotation, member)));
        }
        return ArrayUtils.toArray(list, Class[]::new);
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return classValue(annotation, member, null);
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation, String member) {
        return stringValue(annotation, member, null);
    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member) {
        return booleanValue(annotation, member, null);
    }

    @Override
    public boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return isTrue(annotation, member, null);
    }

    @Override
    public boolean isPresent(Class<? extends Annotation> annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.isPresent(annotation, member)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).stringValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.stringValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        List<String> strings = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            if (am instanceof EnvironmentAnnotationMetadata) {
                strings.addAll(Arrays.asList(((EnvironmentAnnotationMetadata) am).stringValues(annotation, member, valueMapper)));
            } else {
                strings.addAll(Arrays.asList(am.stringValues(annotation, member)));
            }
        }
        return ArrayUtils.toArray(strings, String[]::new);
    }

    @Override
    public String[] stringValues(String annotation, String member, Function<Object, Object> valueMapper) {
        List<String> strings = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            if (am instanceof EnvironmentAnnotationMetadata) {
                strings.addAll(Arrays.asList(((EnvironmentAnnotationMetadata) am).stringValues(annotation, member, valueMapper)));
            } else {
                strings.addAll(Arrays.asList(am.stringValues(annotation, member)));
            }
        }
        return ArrayUtils.toArray(strings, String[]::new);
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).stringValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.stringValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).doubleValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.doubleValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member, Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).doubleValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.doubleValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata) {
                o = ((EnvironmentAnnotationMetadata) annotationMetadata).getValue(annotation, member, requiredType, valueMapper);
            } else {
                o = annotationMetadata.getValue(annotation, member, requiredType);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Iterator<AnnotationMetadata> iterator() {
        return ArrayUtils.reverseIterator(hierarchy);
    }

    private Optional<Class<? extends Annotation>> getAnnotationType(Function<AnnotationMetadata, Optional<Class<? extends Annotation>>> annotationTypeSupplier) {
        for (AnnotationMetadata metadata : hierarchy) {
            final Optional<Class<? extends Annotation>> annotationType = annotationTypeSupplier.apply(metadata);
            if (annotationType.isPresent()) {
                return annotationType;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isEmpty() {
        for (AnnotationMetadata metadata : hierarchy) {
            if (!metadata.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isRepeatableAnnotation(Class<? extends Annotation> annotation) {
        for (AnnotationMetadata metadata : hierarchy) {
            if (metadata.isRepeatableAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRepeatableAnnotation(String annotation) {
        for (AnnotationMetadata metadata : hierarchy) {
            if (metadata.isRepeatableAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<String> findRepeatableAnnotation(Class<? extends Annotation> annotation) {
        for (AnnotationMetadata metadata : hierarchy) {
            Optional<String> repeatable = metadata.findRepeatableAnnotation(annotation);
            if (repeatable.isPresent()) {
                return repeatable;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> findRepeatableAnnotation(String annotation) {
        for (AnnotationMetadata metadata : hierarchy) {
            Optional<String> repeatable = metadata.findRepeatableAnnotation(annotation);
            if (repeatable.isPresent()) {
                return repeatable;
            }
        }
        return Optional.empty();
    }

    /**
     * Merges the hierarchy into one {@link MutableAnnotationMetadata}.
     *
     * @return merged metadata
     * @since 4.0.0
     */
    public MutableAnnotationMetadata merge() {
        MutableAnnotationMetadata newAnnotationMetadata = new MutableAnnotationMetadata();
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            annotationMetadata = annotationMetadata.unwrap();
            if (annotationMetadata.isEmpty()) {
                continue;
            }
            if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                newAnnotationMetadata.addAnnotationMetadata(((AnnotationMetadataHierarchy) annotationMetadata).merge());
            } else if (annotationMetadata instanceof DefaultAnnotationMetadata) {
                newAnnotationMetadata.addAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
            } else {
                throw new IllegalStateException("Unknown instance of AnnotationMetadata: " + annotationMetadata.getClass());
            }
        }
        return newAnnotationMetadata;
    }

    @Override
    public AnnotationMetadata copy() {
        AnnotationMetadata[] copy = new AnnotationMetadata[hierarchy.length];
        System.arraycopy(hierarchy, 0, copy, 0, hierarchy.length);
        ArrayUtils.reverse(copy);
        return new AnnotationMetadataHierarchy(
            delegateDeclaredToAllElements,
            Arrays.stream(copy).map(AnnotationMetadata::copy).toArray(AnnotationMetadata[]::new)
        );
    }

    /**
     * The size of the hierarchy.
     * @return The size
     * @since 4.0.0
     */
    public int size() {
        return hierarchy.length;
    }
}
