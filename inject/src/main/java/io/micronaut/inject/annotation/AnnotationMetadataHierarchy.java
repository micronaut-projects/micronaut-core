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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

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
    public boolean hasEvaluatedExpressions() {
        for (AnnotationMetadata annotationMetadata: hierarchy) {
            if (annotationMetadata.hasEvaluatedExpressions()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        for (AnnotationMetadata metadata : hierarchy) {
            Optional<Class<? extends Annotation>> annotationType = metadata.getAnnotationType(name);
            if (annotationType.isPresent()) {
                return annotationType;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name, ClassLoader classLoader) {
        for (AnnotationMetadata metadata : hierarchy) {
            Optional<Class<? extends Annotation>> annotationType = metadata.getAnnotationType(name, classLoader);
            if (annotationType.isPresent()) {
                return annotationType;
            }
        }
        return Optional.empty();
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    @Override
    public AnnotationMetadata getDeclaredMetadata() {
        return hierarchy[0];
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    public AnnotationMetadata getRootMetadata() {
        return hierarchy[hierarchy.length - 1];
    }

    /**
     * Create a new hierarchy instance from this metadata using this metadata's parents.
     *
     * @param child The child annotation metadata
     * @return A new sibling
     */
    public AnnotationMetadata createSibling(AnnotationMetadata child) {
        if (hierarchy.length > 1) {
            return new AnnotationMetadataHierarchy(hierarchy, child);
        } else {
            return child;
        }
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass, String sourceAnnotation) {
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

    @Override
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
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass, String sourceAnnotation) {
        return hierarchy[0].synthesize(annotationClass, sourceAnnotation);
    }

    /**
     * Synthesizes the annotation from the first element of the hierarchy that declares it, without merging the
     * members of the elements further up. Note that this differs from {@link #findAnnotation(String)}, which
     * completes the members of the nearest occurrence with the members of the occurrences further up.
     *
     * @param annotationClass The annotation class
     * @param <T> The annotation type
     * @return The synthesized annotation or {@code null}
     */
    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass) {
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
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        if (delegateDeclaredToAllElements) {
            return merge().synthesizeDeclared(annotationClass);
        }
        return hierarchy[0].synthesize(annotationClass);
    }

    /**
     * Finds the annotation by merging the occurrences found in the hierarchy: the members of the nearest
     * occurrence win and the members it does not declare are filled in from the occurrences further up. Note
     * that this differs from {@link #synthesize(Class)} and from the single value reads such as
     * {@link #stringValue(String, String)}, which answer from one element of the hierarchy at a time.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return The merged annotation value
     */
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
        return Optional.ofNullable(getAnnotation(annotation));
    }

    @Override
    public <T extends Annotation> @Nullable AnnotationValue<T> getAnnotation(String annotation) {
        // Each layer checks the repeatable registry only when it misses, so checking it up front costs more than it saves
        AnnotationValue<T> existing = null;
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            existing = mergeValue(annotation, existing, annotationMetadata.getAnnotation(annotation));
        }
        return existing;
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(Class<T> annotationClass) {
        // The default checks repeatability before findAnnotation(String), which would check it again
        return Optional.ofNullable(getAnnotation(annotationClass));
    }

    @Override
    public <T extends Annotation> @Nullable AnnotationValue<T> getAnnotation(Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        // As findAnnotation(Class), without creating an Optional to unwrap
        String annotation = annotationClass.getName();
        if (isRepeatableInAnyLayer(annotationClass, annotation)) {
            List<AnnotationValue<T>> values = getAnnotationValuesByType(annotationClass);
            return values.isEmpty() ? null : values.get(0);
        }
        // No layer considers the annotation repeatable, so the layers that are exactly default metadata do not have to
        // check again; a subclass keeps answering through its getAnnotation(String)
        AnnotationValue<T> existing = null;
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            AnnotationValue<T> value = annotationMetadata.getClass() == DefaultAnnotationMetadata.class
                ? ((DefaultAnnotationMetadata) annotationMetadata).getNonRepeatableAnnotation(annotation)
                : annotationMetadata.getAnnotation(annotation);
            existing = mergeValue(annotation, existing, value);
        }
        return existing;
    }

    @Nullable
    private <T extends Annotation> AnnotationValue<T> mergeValue(String annotation,
                                                                 @Nullable AnnotationValue<T> existingValue,
                                                                 @Nullable AnnotationValue<T> newValue) {
        if (newValue == null) {
            return existingValue;
        }
        if (existingValue == null) {
            return newValue;
        }
        final Map<CharSequence, Object> values = newValue.getValues();
        final Map<CharSequence, Object> existing = existingValue.getValues();
        Map<CharSequence, Object> newValues = CollectionUtils.newLinkedHashMap(values.size() + existing.size());
        newValues.putAll(existing);
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            newValues.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<CharSequence, Object> newDefaults = newValue.getDefaultValues();
        Map<CharSequence, Object> existingDefaults = existingValue.getDefaultValues();
        return new AnnotationValue<>(
                annotation,
                newValues,
                existingDefaults != null ? existingDefaults : newDefaults
        );
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
        if (delegateDeclaredToAllElements) {
            AnnotationValue<T> existing = null;
            for (AnnotationMetadata annotationMetadata : hierarchy) {
                existing = mergeValue(annotation, existing, annotationMetadata.getDeclaredAnnotation(annotation));
            }
            return Optional.ofNullable(existing);
        }
        return hierarchy[0].findDeclaredAnnotation(annotation);
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation, String member) {
        return stringValues(annotation.getName(), member);
    }

    @Override
    public String[] stringValues(String annotation, String member) {
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
    public Optional<Boolean> booleanValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o = annotationMetadata.booleanValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(String annotation, String member) {
        for (AnnotationMetadata am : hierarchy) {
            if (am.isTrue(annotation, member)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OptionalLong longValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o = annotationMetadata.longValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<String> stringValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o = annotationMetadata.stringValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o = annotationMetadata.intValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o = annotationMetadata.enumValue(annotation, member, enumType);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Class<T>[] classValues(String annotation, String member) {
        List<Class<T>> list = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            list.addAll(Arrays.asList(am.classValues(annotation, member)));
        }
        return list.toArray(new Class[0]);
    }

    @Override
    public Optional<Class> classValue(String annotation, String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o = annotationMetadata.classValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        List<String> list = null;
        Set<String> uniqueNames = null;
        for (AnnotationMetadata am : hierarchy) {
            List<String> names = am.getAnnotationNamesByStereotype(stereotype);
            for (int i = 0, n = names.size(); i < n; i++) {
                String name = names.get(i);
                if (list == null) {
                    // Sized for the first layer with results: a single result does not need the default capacity
                    list = new ArrayList<>(n);
                    list.add(name);
                    continue;
                }
                if (uniqueNames == null) {
                    // Only created once there is a second name to compare: the list holds the first name only. Few names
                    // are expected, so a small table
                    uniqueNames = new HashSet<>(4);
                    uniqueNames.add(list.get(0));
                }
                if (uniqueNames.add(name)) {
                    list.add(name);
                }
            }
        }
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(@Nullable String stereotype) {
        List<AnnotationValue<T>> list = null;
        Set<AnnotationValue<T>> uniqueValues = null;
        for (AnnotationMetadata am : hierarchy) {
            List<AnnotationValue<T>> values = am.getAnnotationValuesByStereotype(stereotype);
            for (int i = 0, n = values.size(); i < n; i++) {
                AnnotationValue<T> value = values.get(i);
                if (list == null) {
                    // Sized for the first layer with results: a single result does not need the default capacity
                    list = new ArrayList<>(n);
                    list.add(value);
                    continue;
                }
                if (uniqueValues == null) {
                    // Only created once there is a second value to compare: the list holds the first value only. Few values
                    // are expected, so a small table
                    uniqueValues = new HashSet<>(4);
                    uniqueValues.add(list.get(0));
                }
                if (uniqueValues.add(value)) {
                    list.add(value);
                }
            }
        }
        return list != null ? list : new ArrayList<>();
    }

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

    @Override
    public Set<String> getAnnotationNames() {
        Set<String> set = new HashSet<>();
        for (AnnotationMetadata am : hierarchy) {
            set.addAll(am.getAnnotationNames());
        }
        return set;
    }

    @Override
    public Set<String> getStereotypeAnnotationNames() {
        Set<String> set = new HashSet<>();
        for (AnnotationMetadata am : hierarchy) {
            set.addAll(am.getStereotypeAnnotationNames());
        }
        return set;
    }

    @Override
    public Set<String> getDeclaredStereotypeAnnotationNames() {
        if (delegateDeclaredToAllElements) {
            Set<String> set = new HashSet<>();
            for (AnnotationMetadata am : hierarchy) {
                set.addAll(am.getDeclaredStereotypeAnnotationNames());
            }
            return set;
        }
        return hierarchy[0].getDeclaredStereotypeAnnotationNames();
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalValues<T> values = annotationMetadata.getValues(annotation, valueType);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return OptionalValues.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Argument<T> requiredType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> defaultValue = annotationMetadata.getDefaultValue(annotation, member, requiredType);
            if (defaultValue.isPresent()) {
                return defaultValue;
            }
        }
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
        List<AnnotationValue<T>> list = null;
        Set<AnnotationValue<T>> uniqueValues = null;
        for (AnnotationMetadata am : hierarchy) {
            // An index loop: the layers' lists are random access, and their iterators were allocated on every call
            List<AnnotationValue<T>> values = am.getAnnotationValuesByType(annotationType);
            for (int i = 0, n = values.size(); i < n; i++) {
                AnnotationValue<T> value = values.get(i);
                if (list == null) {
                    // Sized for the first layer with results: a single result does not need the default capacity
                    list = new ArrayList<>(n);
                    list.add(value);
                    continue;
                }
                if (uniqueValues == null) {
                    // Only created once there is a second value to compare: the list holds the first value only. Few values
                    // are expected, so a small table
                    uniqueValues = new HashSet<>(4);
                    uniqueValues.add(list.get(0));
                }
                if (uniqueValues.add(value)) {
                    list.add(value);
                }
            }
        }
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        if (annotationType == null) {
            return Collections.emptyList();
        }
        return mergeAnnotationValues(annotationType, AnnotationMetadata::getAnnotationValuesByName);
    }

    private <T extends Annotation, V> List<AnnotationValue<T>> mergeAnnotationValues(V annotationType,
                                                                                     BiFunction<AnnotationMetadata, V, List<AnnotationValue<T>>> fn) {
        List<AnnotationValue<T>> list = null;
        Set<AnnotationValue<T>> uniqueValues = null;
        for (AnnotationMetadata am : hierarchy) {
            List<AnnotationValue<T>> values = fn.apply(am, annotationType);
            for (int i = 0, n = values.size(); i < n; i++) {
                AnnotationValue<T> value = values.get(i);
                if (list == null) {
                    // Sized for the first layer with results: a single result does not need the default capacity
                    list = new ArrayList<>(n);
                    list.add(value);
                    continue;
                }
                if (uniqueValues == null) {
                    // Only created once there is a second value to compare: the list holds the first value only. Few values
                    // are expected, so a small table
                    uniqueValues = new HashSet<>(4);
                    uniqueValues.add(list.get(0));
                }
                if (uniqueValues.add(value)) {
                    list.add(value);
                }
            }
        }
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }


    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
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

    @Override
    public Map<CharSequence, Object> getDefaultValues(String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Map<CharSequence, Object> defaultValues = annotationMetadata.getDefaultValues(annotation);
            if (!defaultValues.isEmpty()) {
                return defaultValues;
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.enumValue(annotation, member, enumType, valueMapper);
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
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.enumValue(annotation, member, enumType, valueMapper);
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
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
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
    public <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
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
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
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
    public Optional<Class> classValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
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
    public OptionalInt intValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
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
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.booleanValue(annotation, member, valueMapper);
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
    public Optional<Boolean> booleanValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.booleanValue(annotation, member, valueMapper);
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
    public OptionalLong longValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.longValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong longValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.longValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public OptionalInt intValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
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
        return list.toArray(new Class[0]);
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
    public Optional<String> stringValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
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
    public String[] stringValues(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        List<String> strings = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            if (am instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                strings.addAll(Arrays.asList(environmentAnnotationMetadata.stringValues(annotation, member, valueMapper)));
            } else {
                strings.addAll(Arrays.asList(am.stringValues(annotation, member)));
            }
        }
        return strings.toArray(EMPTY_STRING_ARRAY);
    }

    @Override
    public String[] stringValues(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        List<String> strings = new ArrayList<>();
        for (AnnotationMetadata am : hierarchy) {
            if (am instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                strings.addAll(Arrays.asList(environmentAnnotationMetadata.stringValues(annotation, member, valueMapper)));
            } else {
                strings.addAll(Arrays.asList(am.stringValues(annotation, member)));
            }
        }
        return strings.toArray(EMPTY_STRING_ARRAY);
    }

    @Override
    public Optional<String> stringValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
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
    public boolean isTrue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public boolean isTrue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.doubleValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
            } else {
                o = annotationMetadata.doubleValue(annotation, member);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> o;
            if (annotationMetadata instanceof EnvironmentAnnotationMetadata environmentAnnotationMetadata) {
                o = environmentAnnotationMetadata.getValue(annotation, member, requiredType, valueMapper);
            } else {
                o = annotationMetadata.getValue(annotation, member, requiredType);
            }
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterator<AnnotationMetadata> iterator() {
        return ArrayUtils.reverseIterator(hierarchy);
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

    /**
     * {@link #isRepeatableAnnotation(Class)}, asking the shared registry once for all the layers that are exactly
     * {@link DefaultAnnotationMetadata}, since that is all such a layer asks.
     *
     * @param annotationClass The annotation
     * @param annotation      The annotation name
     * @return Whether a layer considers the annotation repeatable
     */
    /**
     * {@link #findRepeatableAnnotation(Class)} without an {@link java.util.Optional} per layer, asking the shared
     * registry once for all the layers that are exactly {@link DefaultAnnotationMetadata}.
     *
     * @param annotationClass The annotation
     * @return The container of the first layer that considers the annotation repeatable, or {@code null}
     */
    @Nullable
    private String repeatableContainer(Class<? extends Annotation> annotationClass) {
        boolean registryChecked = false;
        for (AnnotationMetadata metadata : hierarchy) {
            if (metadata.getClass() == DefaultAnnotationMetadata.class) {
                if (!registryChecked) {
                    String container = AnnotationMetadataSupport.getRepeatableAnnotation(annotationClass.getName());
                    if (container != null) {
                        return container;
                    }
                    registryChecked = true;
                }
            } else {
                String container = metadata.findRepeatableAnnotation(annotationClass).orElse(null);
                if (container != null) {
                    return container;
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        String container = repeatableContainer(annotation);
        return hasAnnotation(container != null ? container : annotation.getName());
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        String container = repeatableContainer(annotation);
        return hasStereotype(container != null ? container : annotation.getName());
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        String container = repeatableContainer(annotation);
        return hasDeclaredAnnotation(container != null ? container : annotation.getName());
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        if (stereotype == null) {
            return false;
        }
        String container = repeatableContainer(stereotype);
        return hasDeclaredStereotype(container != null ? container : stereotype.getName());
    }

    private boolean isRepeatableInAnyLayer(Class<? extends Annotation> annotationClass, String annotation) {
        boolean registryChecked = false;
        for (AnnotationMetadata metadata : hierarchy) {
            if (metadata.getClass() == DefaultAnnotationMetadata.class) {
                if (!registryChecked) {
                    if (AnnotationMetadataSupport.getRepeatableAnnotation(annotation) != null) {
                        return true;
                    }
                    registryChecked = true;
                }
            } else if (metadata.isRepeatableAnnotation(annotationClass)) {
                return true;
            }
        }
        return false;
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
            annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
            if (annotationMetadata.isEmpty()) {
                continue;
            }
            if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
                newAnnotationMetadata.addAnnotationMetadata(annotationMetadataHierarchy.merge());
            } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
                newAnnotationMetadata.addAnnotationMetadata(mutableAnnotationMetadata);
            } else if (annotationMetadata instanceof DefaultAnnotationMetadata defaultAnnotationMetadata) {
                newAnnotationMetadata.addAnnotationMetadata(defaultAnnotationMetadata);
            } else {
                throw new IllegalStateException("Unknown instance of AnnotationMetadata: " + annotationMetadata.getClass());
            }
        }
        return newAnnotationMetadata;
    }

    @Override
    public AnnotationMetadata copyAnnotationMetadata() {
        AnnotationMetadata[] copy = new AnnotationMetadata[hierarchy.length];
        System.arraycopy(hierarchy, 0, copy, 0, hierarchy.length);
        ArrayUtils.reverse(copy);
        return new AnnotationMetadataHierarchy(
            delegateDeclaredToAllElements,
            Arrays.stream(copy).map(AnnotationMetadata::copyAnnotationMetadata).toArray(AnnotationMetadata[]::new)
        );
    }

    /**
     * Creates a copy of this hierarchy with every element replaced by the result of the given mapper, which
     * allows the occurrence semantics of the hierarchy to be preserved by wrappers that need to map the
     * annotation values of each element.
     *
     * @param mapper The mapper applied to each element of the hierarchy
     * @return A new hierarchy of the mapped elements
     * @since 5.2.2
     */
    @Internal
    public AnnotationMetadataHierarchy mapLayers(Function<AnnotationMetadata, AnnotationMetadata> mapper) {
        AnnotationMetadata[] mapped = new AnnotationMetadata[hierarchy.length];
        for (int i = 0; i < hierarchy.length; i++) {
            mapped[i] = mapper.apply(hierarchy[i]);
        }
        // the constructor reverses the elements it is given
        ArrayUtils.reverse(mapped);
        return new AnnotationMetadataHierarchy(delegateDeclaredToAllElements, mapped);
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
