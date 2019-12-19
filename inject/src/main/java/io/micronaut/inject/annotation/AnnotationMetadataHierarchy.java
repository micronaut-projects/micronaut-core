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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * Default constructor.
     *
     * @param hierarchy The annotation hierarchy
     */
    public AnnotationMetadataHierarchy(AnnotationMetadata... hierarchy) {
        if (ArrayUtils.isNotEmpty(hierarchy)) {
            // place the first in the hierarchy first
            final int len = hierarchy.length;
            if (len > 1) {
                for (int i = 0; i < len / 2; i++) {
                    AnnotationMetadata temp = hierarchy[i];
                    final int pos = len - i - 1;
                    hierarchy[i] = hierarchy[pos];
                    hierarchy[pos] = temp;
                }
            }
            this.hierarchy = hierarchy;
        } else {
            this.hierarchy = EMPTY_HIERARCHY;
        }
    }

    /**
     * Copy constructor.
     * @param existing Existing
     * @param newChild new child
     */
    private AnnotationMetadataHierarchy(AnnotationMetadata[] existing, AnnotationMetadata newChild) {
        hierarchy = new AnnotationMetadata[existing.length];
        System.arraycopy(existing, 0, hierarchy, 0, existing.length);
        hierarchy[0] = newChild;
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@Nonnull String name) {
        for (AnnotationMetadata metadata : hierarchy) {
            final Optional<Class<? extends Annotation>> annotationType = metadata.getAnnotationType(name);
            if (annotationType.isPresent()) {
                return annotationType;
            }
        }
        return Optional.empty();
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    @Nonnull
    public AnnotationMetadata getDeclaredMetadata() {
        return hierarchy[0];
    }

    /**
     * @return The metadata that is actually declared in the element
     */
    @Nonnull
    public AnnotationMetadata getRootMetadata() {
        return hierarchy[hierarchy.length - 1];
    }

    /**
     * Create a new hierarchy instance from this metadata using this metadata's parents.
     * @param child The child annotation metadata
     * @return A new sibling
     */
    @Nonnull
    public AnnotationMetadata createSibling(@Nonnull AnnotationMetadata child) {
        if (hierarchy.length > 1) {
            return new AnnotationMetadataHierarchy(hierarchy, child);
        } else {
            return child;
        }
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(@Nonnull Class<T> annotationClass) {
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
    public <T extends Annotation> T synthesizeDeclared(@Nonnull Class<T> annotationClass) {
        return hierarchy[0].synthesize(annotationClass);
    }

    @Nonnull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull String annotation) {
        AnnotationValue<T> ann = null;
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final AnnotationValue<T> av = annotationMetadata.getAnnotation(annotation);
            if (av != null) {
                if (ann == null) {
                    ann = av;
                } else {
                    final Map<CharSequence, Object> values = av.getValues();
                    final Map<CharSequence, Object> existing = ann.getValues();
                    Map<CharSequence, Object> newValues = new LinkedHashMap<>(values.size() + existing.size());
                    newValues.putAll(existing);
                    for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
                        newValues.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    ann = new AnnotationValue<>(annotation, newValues);
                }
            }
        }
        return Optional.ofNullable(ann);
    }

    @Nonnull
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull String annotation) {
        return hierarchy[0].findDeclaredAnnotation(annotation);
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> Stream.of(am.stringValues(annotation, member)))
                .toArray(String[]::new);
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o = annotationMetadata.booleanValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        return Arrays.stream(hierarchy).anyMatch(am -> am.isTrue(annotation, member));
    }

    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o = annotationMetadata.longValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o = annotationMetadata.stringValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o = annotationMetadata.intValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o = annotationMetadata.enumValue(annotation, member, enumType);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
        final Class[] classes = Arrays.stream(hierarchy)
                .flatMap(am -> Stream.of(am.classValues(annotation, member)))
                .toArray(Class[]::new);
        return (Class<T>[]) classes;
    }

    @Override
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o = annotationMetadata.classValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getAnnotationNamesByStereotype(stereotype).stream())
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        return hierarchy[0].getDeclaredAnnotationNames();
    }

    @Nonnull
    @Override
    public Set<String> getAnnotationNames() {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getAnnotationNames().stream())
                .collect(Collectors.toSet());
    }

    @Nonnull
    @Override
    public <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalValues<T> values = annotationMetadata.getValues(annotation, valueType);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return OptionalValues.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> defaultValue = annotationMetadata.getDefaultValue(annotation, member, requiredType);
            if (defaultValue.isPresent()) {
                return defaultValue;
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getDeclaredAnnotationValuesByType(annotationType).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return hierarchy[0].getDeclaredAnnotationValuesByType(annotationType);
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return hierarchy[0].hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasStereotype(annotation)) {
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
        return hierarchy[0].hasDeclaredStereotype(annotation);
    }

    @Nonnull
    @Override
    public Map<String, Object> getDefaultValues(@Nonnull String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Map<String, Object> defaultValues = annotationMetadata.getDefaultValues(annotation);
            if (!defaultValues.isEmpty()) {
                return defaultValues;
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
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
    public <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper) {
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
    public Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
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
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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
    public OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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
    public Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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
    public OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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
    public Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> {
                    if (am instanceof EnvironmentAnnotationMetadata) {
                        return Stream.of(((EnvironmentAnnotationMetadata) am).stringValues(annotation, member, valueMapper));
                    }
                    return Stream.of(am.stringValues(annotation, member));
                })
                .toArray(String[]::new);
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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
    public boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
        return booleanValue(annotation, member, valueMapper).orElse(false);
    }

    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member, Function<Object, Object> valueMapper) {
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

    @Nonnull
    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper) {
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

    @NotNull
    @Override
    public Iterator<AnnotationMetadata> iterator() {
        return ArrayUtils.reverseIterator(hierarchy);
    }
}
