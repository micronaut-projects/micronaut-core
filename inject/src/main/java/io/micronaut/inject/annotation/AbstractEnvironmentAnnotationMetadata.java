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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Variation of {@link AnnotationMetadata} that is environment specific.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractEnvironmentAnnotationMetadata extends AbstractAnnotationMetadata implements AnnotationMetadata {

    private final DefaultAnnotationMetadata annotationMetadata;

    /**
     * Construct a new environment aware annotation metadata.
     *
     * @param targetMetadata The target annotation metadata
     */
    protected AbstractEnvironmentAnnotationMetadata(DefaultAnnotationMetadata targetMetadata) {
        super(targetMetadata.declaredAnnotations, targetMetadata.allAnnotations);
        this.annotationMetadata = targetMetadata;
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return annotationMetadata.getValue(annotation, member, requiredType, o -> {
                PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return placeholderResolver.resolveRequiredPlaceholders(v);
                    }
                } else if (o instanceof String[]) {
                    return AnnotationValue.resolveStringArray((String[]) o, o1 -> {
                        String v = (String) o1;
                        if (v.contains("${")) {
                            return placeholderResolver.resolveRequiredPlaceholders(v);
                        }
                        return v;
                    });
                }
                return o;
            });
        } else {
            return annotationMetadata.getValue(annotation, member, requiredType);
        }
    }

    @Override
    public <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
        return annotationMetadata.classValues(annotation, member);
    }

    @Override
    public <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return annotationMetadata.classValues(annotation, member);
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return annotationMetadata.isTrue(annotation, member, o -> {
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return environment.getPlaceholderResolver().resolveRequiredPlaceholders(v);
                    }
                }
                return o;
            });
        } else {
            return annotationMetadata.isTrue(annotation, member);
        }
    }

    @Override
    public boolean isFalse(@Nonnull String annotation, @Nonnull String member) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return !annotationMetadata.isTrue(annotation, member, o -> {
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return environment.getPlaceholderResolver().resolveRequiredPlaceholders(v);
                    }
                }
                return o;
            });
        } else {
            return !annotationMetadata.isTrue(annotation, member);
        }
    }

    @Nonnull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return annotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Nonnull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return annotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Nonnull
    @Override
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.classValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.classValue(annotation, member, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.enumValue(annotation, member,  enumType, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.enumValue(annotation, member,  enumType, valueMapper);
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.booleanValue(annotation, member,  valueMapper);

    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.booleanValue(annotation, member,  valueMapper);
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.stringValues(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.longValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.longValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.intValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.intValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @Override
    public boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return annotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public boolean isFalse(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return !annotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public @Nonnull Optional<Class<? extends Annotation>> getAnnotationType(@Nonnull String name) {
        ArgumentUtils.requireNonNull("name", name);
        return annotationMetadata.getAnnotationType(name);
    }

    @Override
    public @Nonnull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = annotationMetadata.getAnnotationValuesByType(annotationType);
        if (environment != null) {
            return values.stream().map(entries ->
                    new EnvironmentAnnotationValue<>(environment, entries)
            ).collect(Collectors.toList());
        }
        return values;
    }

    @Override
    public @Nonnull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = annotationMetadata.getDeclaredAnnotationValuesByType(annotationType);
        if (environment != null) {
            return values.stream().map(entries -> new EnvironmentAnnotationValue<>(environment, entries))
                    .collect(Collectors.toList());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T extends Annotation> T[] synthesizeAnnotationsByType(@Nonnull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        Environment environment = getEnvironment();
        if (environment != null) {

            List<AnnotationValue<T>> values = annotationMetadata.getAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, EnvironmentConvertibleValuesMap.of(environment, entries.getValues())))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return annotationMetadata.synthesizeAnnotationsByType(annotationClass);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@Nonnull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        Environment environment = getEnvironment();
        if (environment != null) {

            List<AnnotationValue<T>> values = annotationMetadata.getDeclaredAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, EnvironmentConvertibleValuesMap.of(environment, entries.getValues())))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return annotationMetadata.synthesizeDeclaredAnnotationsByType(annotationClass);
        }
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return annotationMetadata.hasStereotype(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredStereotype(annotation);
    }

    @Override
    public @Nonnull List<String> getAnnotationNamesByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public @Nonnull Set<String> getAnnotationNames() {
        return annotationMetadata.getAnnotationNames();
    }

    @Override
    public @Nonnull Set<String> getDeclaredAnnotationNames() {
        return annotationMetadata.getDeclaredAnnotationNames();
    }

    @Override
    public @Nonnull List<String> getDeclaredAnnotationNamesByStereotype(String stereotype) {
        return annotationMetadata.getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = annotationMetadata.findAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = annotationMetadata.findDeclaredAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @Nonnull <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("valueType", valueType);
        Map<String, Map<CharSequence, Object>> allAnnotations = annotationMetadata.allAnnotations;
        Map<String, Map<CharSequence, Object>> allStereotypes = annotationMetadata.allStereotypes;
        Environment environment = getEnvironment();
        OptionalValues<T> values = resolveOptionalValuesForEnvironment(annotation, valueType, allAnnotations, allStereotypes, environment);
        if (values != null) {
            return values;
        }
        return OptionalValues.empty();
    }

    @Override
    public @Nonnull <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return annotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public @Nonnull <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return annotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    /**
     * Resolves the {@link Environment} for this metadata.
     *
     * @return The metadata
     */
    protected abstract @Nullable Environment getEnvironment();

    @Override
    protected void addValuesToResults(List<AnnotationValue> results, AnnotationValue values) {
        Environment environment = getEnvironment();
        if (environment != null) {
            results.add(new EnvironmentAnnotationValue<>(environment, values));
        } else {
            results.add(values);
        }
    }

    /**
     * @return The value mapper for the environment
     */
    private @Nullable Function<Object, Object> getEnvironmentValueMapper() {
        Environment env = getEnvironment();
        if (env != null) {
            return o -> {
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return env.getPlaceholderResolver().resolveRequiredPlaceholders(v);
                    }
                }
                return o;
            };
        }
        return null;
    }

    private <T> OptionalValues<T> resolveOptionalValuesForEnvironment(String annotation, Class<T> valueType, Map<String, Map<CharSequence, Object>> allAnnotations, Map<String, Map<CharSequence, Object>> allStereotypes, Environment environment) {
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                if (environment != null) {
                    return new EnvironmentOptionalValuesMap<>(valueType, values, environment);
                } else {
                    return OptionalValues.of(valueType, values);
                }
            } else {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    if (environment != null) {
                        return new EnvironmentOptionalValuesMap<>(valueType, values, environment);
                    } else {
                        return OptionalValues.of(valueType, values);
                    }
                }
            }
        }
        return null;
    }
}
