/*
 * Copyright 2017-2020 original authors
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
public abstract class AbstractEnvironmentAnnotationMetadata implements AnnotationMetadata {

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
    public AnnotationMetadata getAnnotationMetadata() {
        return environmentAnnotationMetadata;
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        return environmentAnnotationMetadata.synthesize(annotationClass);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        return environmentAnnotationMetadata.synthesizeDeclared(annotationClass);
    }

    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
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
    public <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        return environmentAnnotationMetadata.classValues(annotation, member);
    }

    @Override
    public <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return environmentAnnotationMetadata.classValues(annotation, member);
    }

    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member) {
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
    public boolean isFalse(@NonNull String annotation, @NonNull String member) {
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

    @NonNull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return environmentAnnotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @NonNull
    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return environmentAnnotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @NonNull
    @Override
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.classValue(annotation, member, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.enumValue(annotation, member, enumType, valueMapper);
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.enumValue(annotation, member, enumType, valueMapper);
    }

    @Override
    public <E extends Enum> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.enumValues(annotation, member, enumType, valueMapper);
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.booleanValue(annotation, member, valueMapper);

    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.booleanValue(annotation, member,  valueMapper);
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
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

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.stringValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
    }

    @Override
    public OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.longValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.intValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.doubleValue(annotation, member, valueMapper);
    }

    @Override
    public boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return environmentAnnotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public boolean isFalse(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        Function<Object, Object> valueMapper = getEnvironmentValueMapper();
        return !environmentAnnotationMetadata.isTrue(annotation, member, valueMapper);
    }

    @Override
    public @NonNull Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        return environmentAnnotationMetadata.getAnnotationType(name);
    }

    @Override
    public @NonNull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@NonNull Class<T> annotationType) {
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
    public @NonNull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
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
    public @NonNull <T extends Annotation> T[] synthesizeAnnotationsByType(@NonNull Class<T> annotationClass) {
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
    public @NonNull <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@NonNull Class<T> annotationClass) {
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
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return environmentAnnotationMetadata.hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        return environmentAnnotationMetadata.hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return environmentAnnotationMetadata.hasStereotype(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return environmentAnnotationMetadata.hasDeclaredStereotype(annotation);
    }

    @Override
    public @NonNull List<String> getAnnotationNamesByStereotype(String stereotype) {
        return environmentAnnotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public @NonNull Set<String> getAnnotationNames() {
        return environmentAnnotationMetadata.getAnnotationNames();
    }

    @Override
    public @NonNull Set<String> getDeclaredAnnotationNames() {
        return environmentAnnotationMetadata.getDeclaredAnnotationNames();
    }

    @Override
    public @NonNull List<String> getDeclaredAnnotationNamesByStereotype(String stereotype) {
        return environmentAnnotationMetadata.getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findDeclaredAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public @NonNull <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
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

    @Override
    public @NonNull <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return environmentAnnotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public @NonNull <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return environmentAnnotationMetadata.getDefaultValue(annotation, member, requiredType);
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
