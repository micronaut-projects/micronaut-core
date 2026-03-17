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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
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

    private final Function<Object, Object> mapFn = new Function<>() {
        @Override
        public Object apply(Object value) {
            Environment environment = AbstractEnvironmentAnnotationMetadata.this.getEnvironment();
            if (environment == null) {
                return value;
            }
            if (value instanceof String v) {
                PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
                if (v.contains(placeholderResolver.getPrefix())) {
                    return placeholderResolver.resolveRequiredPlaceholders(v);
                }
            } else if (value instanceof String[] strings) {
                return AnnotationValue.resolveStringArray(strings, this);
            }
            return value;
        }
    };

    /**
     * Construct a new environment aware annotation metadata.
     *
     * @param targetMetadata The target annotation metadata
     */
    protected AbstractEnvironmentAnnotationMetadata(AnnotationMetadata targetMetadata) {
        if (targetMetadata instanceof EnvironmentAnnotationMetadata metadata) {
            this.environmentAnnotationMetadata = metadata;
        } else {
            this.environmentAnnotationMetadata = new AnnotationMetadataHierarchy(targetMetadata);
        }
    }

    @Nullable
    private Function<Object, Object> mapFnOrNull() {
        return getEnvironment() == null ? null : mapFn;
    }

    /**
     * @return The backing annotation metadata
     */
    public AnnotationMetadata getAnnotationMetadata() {
        return environmentAnnotationMetadata;
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass) {
        return environmentAnnotationMetadata.synthesize(annotationClass);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        return environmentAnnotationMetadata.synthesizeDeclared(annotationClass);
    }

    @Override
    public boolean hasEvaluatedExpressions() {
        return environmentAnnotationMetadata.hasEvaluatedExpressions();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType) {
        return environmentAnnotationMetadata.getValue(annotation, member, requiredType, mapFnOrNull());
    }

    @Override
    public <T> Class<T>[] classValues(String annotation, String member) {
        return environmentAnnotationMetadata.classValues(annotation, member);
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.classValues(annotation, member);
    }

    @Override
    public boolean isTrue(String annotation, String member) {
        return environmentAnnotationMetadata.isTrue(annotation, member, mapFnOrNull());
    }

    @Override
    public boolean isFalse(String annotation, String member) {
        return !environmentAnnotationMetadata.isTrue(annotation, member, mapFnOrNull());
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return environmentAnnotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return environmentAnnotationMetadata.getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    public Optional<Class> classValue(String annotation, String member) {
        return environmentAnnotationMetadata.classValue(annotation, member, mapFnOrNull());
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.classValue(annotation, member, mapFnOrNull());
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType) {
        return environmentAnnotationMetadata.enumValue(annotation, member, enumType, mapFnOrNull());
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return environmentAnnotationMetadata.enumValue(annotation, member, enumType, mapFnOrNull());
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType) {
        return environmentAnnotationMetadata.enumValues(annotation, member, enumType, mapFnOrNull());
    }

    @Override
    public Optional<Boolean> booleanValue(String annotation, String member) {
        return environmentAnnotationMetadata.booleanValue(annotation, member, mapFnOrNull());

    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.booleanValue(annotation, member, mapFnOrNull());
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.stringValue(annotation, member, mapFnOrNull());
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation, String member) {
        Environment environment = getEnvironment();
        if (environment != null) {

            PropertyPlaceholderResolver resolver = environment.getPlaceholderResolver();
            Function<Object, @Nullable Object> valueMapper = val -> {
                @Nullable String[] values;
                if (val instanceof CharSequence) {
                    values = new String[]{val.toString()};
                } else if (val instanceof String[] strings) {
                    values = strings;
                } else {
                    return null;
                }
                String[] resolvedValues = Arrays.copyOf(values, values.length);
                boolean expandValues = false;
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (value != null && value.contains(resolver.getPrefix())) {
                        value = resolver.resolveRequiredPlaceholders(value);
                        if (value.contains(",")) {
                            expandValues = true;
                        }
                    }
                    resolvedValues[i] = value;
                }
                if (expandValues) {
                    return Stream.of(resolvedValues).flatMap(s -> {
                        if (s.contains(",")) {
                            return Arrays.stream(resolver.resolveRequiredPlaceholder(s, String[].class));
                        }
                        return Stream.of(s);
                    }).toArray(String[]::new);
                } else {
                    return resolvedValues;
                }
            };
            return environmentAnnotationMetadata.stringValues(annotation, member, valueMapper);
        } else {
            return environmentAnnotationMetadata.stringValues(annotation, member, null);
        }
    }

    @Override
    public Optional<String> stringValue(String annotation, String member) {
        return environmentAnnotationMetadata.stringValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalLong longValue(String annotation, String member) {
        return environmentAnnotationMetadata.longValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalLong longValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.longValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalInt intValue(String annotation, String member) {
        return environmentAnnotationMetadata.intValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.intValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member) {
        return environmentAnnotationMetadata.doubleValue(annotation, member, mapFnOrNull());
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.doubleValue(annotation, member, mapFnOrNull());
    }

    @Override
    public boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return environmentAnnotationMetadata.isTrue(annotation, member, mapFnOrNull());
    }

    @Override
    public boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return !environmentAnnotationMetadata.isTrue(annotation, member, mapFnOrNull());
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        return environmentAnnotationMetadata.getAnnotationType(name);
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name, ClassLoader classLoader) {
        return environmentAnnotationMetadata.getAnnotationType(name, classLoader);
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
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
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
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
    public <T extends Annotation> T[] synthesizeAnnotationsByType(Class<T> annotationClass) {
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
    public <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(Class<T> annotationClass) {
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
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return environmentAnnotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public Set<String> getAnnotationNames() {
        return environmentAnnotationMetadata.getAnnotationNames();
    }

    @Override
    public Set<String> getDeclaredAnnotationNames() {
        return environmentAnnotationMetadata.getDeclaredAnnotationNames();
    }

    @Override
    public List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        return environmentAnnotationMetadata.getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        Environment env = getEnvironment();

        Optional<AnnotationValue<T>> values = environmentAnnotationMetadata.findDeclaredAnnotation(annotation);

        if (env != null) {
            return values.map(av -> new EnvironmentAnnotationValue<>(env, av));
        }
        return values;
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
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
        } else if (environmentAnnotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
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
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return environmentAnnotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Argument<T> requiredType) {
        return environmentAnnotationMetadata.getDefaultValue(annotation, member, requiredType);
    }

    @Override
    public AnnotationMetadata copyAnnotationMetadata() {
        return environmentAnnotationMetadata.copyAnnotationMetadata();
    }

    @Override
    public AnnotationMetadata getTargetAnnotationMetadata() {
        return environmentAnnotationMetadata.getTargetAnnotationMetadata();
    }

    /**
     * Resolves the {@link Environment} for this metadata.
     *
     * @return The metadata
     */
    protected abstract @Nullable Environment getEnvironment();

    private <T> OptionalValues<T> resolveOptionalValuesForEnvironment(
        String annotation,
        Class<T> valueType,
        Iterable<AnnotationMetadata> metadata,
        @Nullable Environment environment) {

        Map<CharSequence, Object> finalValues = new LinkedHashMap<>();
        for (AnnotationMetadata annotationMetadata : metadata) {
            if (annotationMetadata instanceof DefaultAnnotationMetadata defaultAnnotationMetadata) {

                Map<String, Map<CharSequence, Object>> allAnnotations = defaultAnnotationMetadata.allAnnotations;
                Map<String, Map<CharSequence, Object>> allStereotypes = defaultAnnotationMetadata.allStereotypes;
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

    private void processMap(String annotation, Map<CharSequence, Object> finalValues, @Nullable Map<String, Map<CharSequence, Object>> allStereotypes) {
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
