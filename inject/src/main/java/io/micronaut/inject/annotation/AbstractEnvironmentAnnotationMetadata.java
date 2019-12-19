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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.*;
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
import java.util.stream.Stream;

/**
 * Variation of {@link AnnotationMetadata} that is environment specific.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractEnvironmentAnnotationMetadata implements AnnotationMetadataDelegate {

    private final EnvironmentAnnotationMetadata environmentAnnotationMetadata;

    /**
     * Construct a new environment aware annotation metadata.
     *
     * @param targetMetadata The target annotation metadata
     */
    protected AbstractEnvironmentAnnotationMetadata(AnnotationMetadata targetMetadata) {
        if (targetMetadata instanceof EnvironmentAnnotationMetadata) {
            this.environmentAnnotationMetadata = (EnvironmentAnnotationMetadata) targetMetadata;
        } else {
            this.environmentAnnotationMetadata = new AnnotationMetadataHierarchy(targetMetadata);
        }
    }

    /**
     * @return The backing annotation metadata
     */
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return environmentAnnotationMetadata;
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return environmentAnnotationMetadata.getValue(annotation, member, requiredType, o -> {
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
            return environmentAnnotationMetadata.getValue(annotation, member, requiredType);
        }
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return environmentAnnotationMetadata.isTrue(annotation, member, o -> {
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return environment.getPlaceholderResolver().resolveRequiredPlaceholders(v);
                    }
                }
                return o;
            });
        } else {
            return environmentAnnotationMetadata.isTrue(annotation, member);
        }
    }

    @Override
    public boolean isFalse(@Nonnull String annotation, @Nonnull String member) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return !environmentAnnotationMetadata.isTrue(annotation, member, o -> {
                if (o instanceof String) {
                    String v = (String) o;
                    if (v.contains("${")) {
                        return environment.getPlaceholderResolver().resolveRequiredPlaceholders(v);
                    }
                }
                return o;
            });
        } else {
            return !environmentAnnotationMetadata.isTrue(annotation, member);
        }
    }

    @Nonnull
    @Override
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.enumValue(annotation, member,  enumType, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.enumValue(annotation, member,  enumType, valueMapper);
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.booleanValue(annotation, member, valueMapper);

    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.booleanValue(annotation, member,  valueMapper);
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Environment environment = getEnvironment();
        if (environment != null) {

            PropertyPlaceholderResolver resolver = environment.getPlaceholderResolver();
            Function<Object, Object> valueMapper = (val) -> {
                String[] values;
                if (val instanceof CharSequence) {
                    values = new String[] { val.toString() };
                } else if (val instanceof String[]) {
                    values = (String[]) val;
                } else {
                    return null;
                }
                return Arrays.stream(values)
                        .flatMap(value -> {
                            try {
                                return Arrays.stream(resolver.resolveRequiredPlaceholder(value, String[].class));
                            } catch (ConfigurationException e) {
                                if (value.contains(resolver.getPrefix())) {
                                    value = resolver.resolveRequiredPlaceholders(value);
                                }
                                return Stream.of(value);
                            }
                        })
                        .toArray(String[]::new);
            };
            return environmentAnnotationMetadata.stringValues(annotation, member, valueMapper);
        } else {
            return environmentAnnotationMetadata.stringValues(annotation, member, null);
        }
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @Override
    public boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public boolean isFalse(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return !environmentAnnotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public @Nonnull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = environmentAnnotationMetadata.getAnnotationValuesByType(annotationType);
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
        List<AnnotationValue<T>> values = environmentAnnotationMetadata.getDeclaredAnnotationValuesByType(annotationType);
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

            List<AnnotationValue<T>> values = environmentAnnotationMetadata.getAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, new EnvironmentAnnotationValue<>(environment, entries)))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return environmentAnnotationMetadata.synthesizeAnnotationsByType(annotationClass);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@Nonnull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        Environment environment = getEnvironment();
        if (environment != null) {

            List<AnnotationValue<T>> values = environmentAnnotationMetadata.getDeclaredAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, new EnvironmentAnnotationValue<>(environment, entries)))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return environmentAnnotationMetadata.synthesizeDeclaredAnnotationsByType(annotationClass);
        }
    }

    @Override
    public @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findDeclaredAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @Nonnull <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("valueType", valueType);

        if (environmentAnnotationMetadata instanceof DefaultAnnotationMetadata) {
            Environment environment = getEnvironment();
            return resolveOptionalValuesForEnvironment(
                    annotation,
                    valueType,
                    Collections.singleton(environmentAnnotationMetadata),
                    environment
            );
        } else if (environmentAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataHierarchy hierarchy = (AnnotationMetadataHierarchy) environmentAnnotationMetadata;
            Environment environment = getEnvironment();
            return resolveOptionalValuesForEnvironment(
                    annotation,
                    valueType,
                    hierarchy,
                    environment
            );

        }
        return OptionalValues.empty();
    }

    /**
     * Resolves the {@link Environment} for this metadata.
     *
     * @return The metadata
     */
    protected abstract @Nullable Environment getEnvironment();

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

    private <T> OptionalValues<T> resolveOptionalValuesForEnvironment(
            String annotation,
            Class<T> valueType,
            Iterable<AnnotationMetadata> metadata,
            Environment environment) {

        Map<CharSequence, Object> finalValues = new LinkedHashMap<>();
        for (AnnotationMetadata annotationMetadata : metadata) {
            if (annotationMetadata instanceof DefaultAnnotationMetadata) {

                Map<String, Map<CharSequence, Object>> allAnnotations = ((DefaultAnnotationMetadata) annotationMetadata).allAnnotations;
                Map<String, Map<CharSequence, Object>> allStereotypes = ((DefaultAnnotationMetadata) annotationMetadata).allStereotypes;
                if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
                    processMap(annotation, finalValues, allStereotypes);
                    processMap(annotation, finalValues, allAnnotations);
                }
            }
        }

        if (environment != null) {
            return new EnvironmentOptionalValuesMap<>(valueType, finalValues, environment);
        } else {
            return OptionalValues.of(valueType, finalValues);
        }
    }

    private void processMap(String annotation, Map<CharSequence, Object> finalValues, Map<String, Map<CharSequence, Object>> allStereotypes) {
        if (allStereotypes != null) {
            Map<CharSequence, Object> values = allStereotypes.get(annotation);
            if (values != null) {
                for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
                    finalValues.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
