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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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

    private static final Object UNRESOLVED = new Object();

    private static final String MEMBER_ANNOTATION_CLASS = "annotationClass";

    private final AnnotationMetadata targetMetadata;

    private final EnvironmentAnnotationMetadata environmentAnnotationMetadata;

    private final AtomicReference<LazyState> lazyState = new AtomicReference<>(LazyState.EMPTY);

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
        this.targetMetadata = targetMetadata;
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

    /**
     * Narrows the target metadata to its declared metadata, keeping the placeholder resolution of this metadata.
     *
     * @return The declared metadata
     */
    @Override
    public AnnotationMetadata getDeclaredMetadata() {
        AnnotationMetadata metadata = lazyState().declaredMetadata();
        if (metadata != null) {
            return metadata;
        }
        AnnotationMetadata declaredTarget = declaredTargetMetadata();
        AnnotationMetadata newMetadata;
        if (declaredTarget == targetMetadata) {
            newMetadata = this;
        } else {
            // kept wrapped even without property expressions, the defaults of the annotation types can hold placeholders
            newMetadata = new DeclaredEnvironmentAnnotationMetadata(declaredTarget, this);
        }
        while (true) {
            LazyState state = lazyState();
            AnnotationMetadata existing = state.declaredMetadata();
            if (existing != null) {
                return existing;
            }
            if (lazyState.compareAndSet(state, state.withDeclaredMetadata(newMetadata))) {
                return newMetadata;
            }
        }
    }

    private AnnotationMetadata declaredTargetMetadata() {
        AnnotationMetadata metadata = lazyState().declaredTargetMetadata();
        if (metadata != null) {
            return metadata;
        }
        AnnotationMetadata newMetadata;
        if (targetMetadata instanceof AnnotationMetadataHierarchy hierarchy && hierarchy.isDelegateDeclaredToAllElements()) {
            // every layer of the hierarchy is declared
            newMetadata = targetMetadata;
        } else {
            newMetadata = targetMetadata.getDeclaredMetadata();
        }
        while (true) {
            LazyState state = lazyState();
            AnnotationMetadata existing = state.declaredTargetMetadata();
            if (existing != null) {
                return existing;
            }
            if (lazyState.compareAndSet(state, state.withDeclaredTargetMetadata(newMetadata))) {
                return newMetadata;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return environmentAnnotationMetadata.isEmpty();
    }

    @Override
    public boolean hasPropertyExpressions() {
        return environmentAnnotationMetadata.hasPropertyExpressions();
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass) {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.synthesize(annotationClass);
        }
        ArgumentUtils.requireNonNull(MEMBER_ANNOTATION_CLASS, annotationClass);
        return synthesizeForEnvironment(environment, annotationClass, annotationClass.getName(), false);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass, String sourceAnnotation) {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.synthesize(annotationClass, sourceAnnotation);
        }
        return synthesizeForEnvironment(environment, annotationClass, sourceAnnotation, false);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.synthesizeDeclared(annotationClass);
        }
        ArgumentUtils.requireNonNull(MEMBER_ANNOTATION_CLASS, annotationClass);
        return synthesizeForEnvironment(environment, annotationClass, annotationClass.getName(), true);
    }

    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass, String sourceAnnotation) {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.synthesizeDeclared(annotationClass, sourceAnnotation);
        }
        return synthesizeForEnvironment(environment, annotationClass, sourceAnnotation, true);
    }

    @Override
    public Annotation[] synthesizeAll() {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.synthesizeAll();
        }
        SynthesizedAnnotations synthesized = synthesizedAnnotations(environment);
        Annotation[] annotations = synthesized.all.get();
        if (annotations == null) {
            annotations = synthesizeForEnvironment(environment, environmentAnnotationMetadata, false, synthesized, true);
        }
        return annotations;
    }

    /**
     * Synthesizes the annotations declared on the element of the {@link #getDeclaredMetadata() declared metadata}.
     *
     * @return The declared annotations
     */
    @Override
    public Annotation[] synthesizeDeclared() {
        AnnotationMetadata declaredTarget = declaredTargetMetadata();
        AnnotationMetadata declared = declaredTarget == targetMetadata ? environmentAnnotationMetadata : declaredTarget;
        Environment environment = getEnvironment();
        if (environment == null) {
            return declared.synthesizeDeclared();
        }
        SynthesizedAnnotations synthesized = synthesizedAnnotations(environment);
        Annotation[] annotations = synthesized.declared.get();
        if (annotations == null) {
            annotations = synthesizeForEnvironment(environment, declared, true, synthesized, false);
        }
        return annotations;
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
            return environmentAnnotationMetadata.stringValues(annotation, member, stringValuesMapper(environment));
        } else {
            return environmentAnnotationMetadata.stringValues(annotation, member, null);
        }
    }

    @Override
    public String[] stringValues(String annotation, String member) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return environmentAnnotationMetadata.stringValues(annotation, member, stringValuesMapper(environment));
        } else {
            return environmentAnnotationMetadata.stringValues(annotation, member, null);
        }
    }

    private static UnaryOperator<@Nullable Object> stringValuesMapper(Environment environment) {
        PropertyPlaceholderResolver resolver = environment.getPlaceholderResolver();
        return val -> {
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
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = environmentAnnotationMetadata.getAnnotationValuesByName(annotationType);
        if (environment != null) {
            return values.stream().map(entries ->
                new EnvironmentAnnotationValue<>(environment, entries)
            ).collect(Collectors.toList());
        }
        return values;
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(@Nullable String stereotype) {
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = environmentAnnotationMetadata.getAnnotationValuesByStereotype(stereotype);
        if (environment != null) {
            return values.stream().map(entries -> new EnvironmentAnnotationValue<>(environment, entries))
                .collect(Collectors.toList());
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

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(String annotationType) {
        Environment environment = getEnvironment();
        List<AnnotationValue<T>> values = environmentAnnotationMetadata.getDeclaredAnnotationValuesByName(annotationType);
        if (environment != null) {
            return values.stream().map(entries -> new EnvironmentAnnotationValue<>(environment, entries))
                .collect(Collectors.toList());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T[] synthesizeAnnotationsByType(Class<T> annotationClass) {
        ArgumentUtils.requireNonNull(MEMBER_ANNOTATION_CLASS, annotationClass);
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
        ArgumentUtils.requireNonNull(MEMBER_ANNOTATION_CLASS, annotationClass);
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
    public Set<String> getStereotypeAnnotationNames() {
        return environmentAnnotationMetadata.getStereotypeAnnotationNames();
    }

    @Override
    public Set<String> getDeclaredStereotypeAnnotationNames() {
        return environmentAnnotationMetadata.getDeclaredStereotypeAnnotationNames();
    }

    @Override
    public boolean isRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return environmentAnnotationMetadata.isRepeatableAnnotation(annotation);
    }

    @Override
    public boolean isRepeatableAnnotation(String annotation) {
        return environmentAnnotationMetadata.isRepeatableAnnotation(annotation);
    }

    @Override
    public Optional<String> findRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return environmentAnnotationMetadata.findRepeatableAnnotation(annotation);
    }

    @Override
    public Optional<String> findRepeatableAnnotation(String annotation) {
        return environmentAnnotationMetadata.findRepeatableAnnotation(annotation);
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
    public Map<CharSequence, Object> getDefaultValues(String annotation) {
        Map<CharSequence, Object> defaultValues = environmentAnnotationMetadata.getDefaultValues(annotation);
        Environment environment = getEnvironment();
        if (environment == null || defaultValues.isEmpty()) {
            return defaultValues;
        }
        // string arrays expand comma separated values like the environment annotation values do
        UnaryOperator<@Nullable Object> arrayMapper = stringValuesMapper(environment);
        Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>(defaultValues.size());
        defaultValues.forEach((member, value) -> resolvedValues.put(member, resolveOrKeep(value instanceof String[] ? arrayMapper : mapFn::apply, value)));
        return Collections.unmodifiableMap(resolvedValues);
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return getDefaultValue(annotation, member, Argument.of(requiredType));
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Argument<T> requiredType) {
        Environment environment = getEnvironment();
        if (environment == null) {
            return environmentAnnotationMetadata.getDefaultValue(annotation, member, requiredType);
        }
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);
        Object value = environmentAnnotationMetadata.getDefaultValues(annotation).get(member);
        if (value instanceof String[]) {
            value = stringValuesMapper(environment).apply(value);
        } else if (value != null) {
            value = mapFn.apply(value);
        }
        if (value != null) {
            return ConversionService.SHARED.convert(value, requiredType);
        }
        return Optional.empty();
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

    private SynthesizedAnnotations synthesizedAnnotations(Environment environment) {
        while (true) {
            LazyState state = lazyState();
            SynthesizedAnnotations synthesized = state.synthesizedAnnotations();
            if (synthesized != null && synthesized.environment == environment) {
                return synthesized;
            }
            SynthesizedAnnotations newSynthesized = new SynthesizedAnnotations(environment);
            if (lazyState.compareAndSet(state, state.withSynthesizedAnnotations(newSynthesized))) {
                return newSynthesized;
            }
        }
    }

    private LazyState lazyState() {
        return Objects.requireNonNull(lazyState.get());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T extends Annotation> T synthesizeForEnvironment(Environment environment,
                                                              Class<T> annotationClass,
                                                              String sourceAnnotation,
                                                              boolean declared) {
        ArgumentUtils.requireNonNull(MEMBER_ANNOTATION_CLASS, annotationClass);
        ArgumentUtils.requireNonNull("sourceAnnotation", sourceAnnotation);
        SynthesizedAnnotations synthesized = synthesizedAnnotations(environment);
        Map<String, Annotation> annotations = declared ? synthesized.declaredByName : synthesized.allByName;
        String key = annotationClass.getName() + '#' + sourceAnnotation;
        Annotation annotation = annotations.get(key);
        if (annotation == null) {
            Optional<AnnotationValue<T>> value = findAnnotationToSynthesize(environmentAnnotationMetadata, sourceAnnotation, declared);
            if (value.isEmpty()) {
                return null;
            }
            ResolvedAnnotation<T> resolved = buildResolvedAnnotation(environment, annotationClass, value.get());
            annotation = resolved.annotation();
            if (!resolved.environmentDependent()) {
                Annotation existing = annotations.putIfAbsent(key, annotation);
                if (existing != null) {
                    annotation = existing;
                }
            }
        }
        return (T) annotation;
    }

    /**
     * Synthesizes the annotations, caching them when none of them depends on the environment: the values resolved
     * from the environment are not cached, so that they follow the changes of the environment.
     */
    private Annotation[] synthesizeForEnvironment(Environment environment,
                                                  AnnotationMetadata metadata,
                                                  boolean declared,
                                                  SynthesizedAnnotations synthesized,
                                                  boolean all) {
        List<Annotation> annotations = new ArrayList<>();
        boolean environmentDependent = synthesizeForEnvironment(environment, metadata, declared, annotations);
        Annotation[] array = annotations.toArray(AnnotationUtil.ZERO_ANNOTATIONS);
        if (!environmentDependent) {
            if (all) {
                synthesized.all.set(array);
            } else {
                synthesized.declared.set(array);
            }
        }
        return array;
    }

    /**
     * Synthesizes the annotations of each layer of a hierarchy, most specific first, like
     * {@link AnnotationMetadataHierarchy#synthesizeAll()} and {@link AnnotationMetadataHierarchy#synthesizeDeclared()}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean synthesizeForEnvironment(Environment environment, AnnotationMetadata metadata, boolean declared, List<Annotation> annotations) {
        boolean environmentDependent = false;
        if (metadata instanceof AnnotationMetadataHierarchy hierarchy) {
            for (AnnotationMetadata layer : layersMostSpecificFirst(hierarchy)) {
                environmentDependent |= synthesizeForEnvironment(environment, layer, declared, annotations);
            }
            return environmentDependent;
        }
        Set<String> annotationNames = declared ? metadata.getDeclaredAnnotationNames() : metadata.getAnnotationNames();
        for (String annotationName : annotationNames) {
            Class annotationClass = metadata.getAnnotationType(annotationName).orElse(null);
            if (annotationClass != null) {
                Optional<AnnotationValue<Annotation>> value = declared
                    ? metadata.findDeclaredAnnotation(annotationName)
                    : metadata.findAnnotation(annotationName);
                if (value.isPresent()) {
                    ResolvedAnnotation<?> resolved = buildResolvedAnnotation(environment, annotationClass, value.get());
                    annotations.add(resolved.annotation());
                    environmentDependent |= resolved.environmentDependent();
                }
            }
        }
        return environmentDependent;
    }

    /**
     * Finds the annotation value to synthesize like {@link AnnotationMetadataHierarchy#synthesize(Class, String)} and
     * {@link AnnotationMetadataHierarchy#synthesizeDeclared(Class, String)}: from the most specific element of a
     * hierarchy that has the annotation, without merging the members of the elements further up.
     */
    private static <T extends Annotation> Optional<AnnotationValue<T>> findAnnotationToSynthesize(AnnotationMetadata metadata,
                                                                                               String annotation,
                                                                                               boolean declared) {
        if (metadata instanceof AnnotationMetadataHierarchy hierarchy) {
            if (declared && hierarchy.isDelegateDeclaredToAllElements()) {
                return hierarchy.findDeclaredAnnotation(annotation);
            }
            for (AnnotationMetadata layer : layersMostSpecificFirst(hierarchy)) {
                Optional<AnnotationValue<T>> value = findAnnotationToSynthesize(layer, annotation, false);
                if (value.isPresent() || declared) {
                    // the declared annotation is the one of the most specific element
                    return value;
                }
            }
            return Optional.empty();
        }
        return declared ? metadata.findDeclaredAnnotation(annotation) : metadata.findAnnotation(annotation);
    }

    private static List<AnnotationMetadata> layersMostSpecificFirst(AnnotationMetadataHierarchy hierarchy) {
        List<AnnotationMetadata> layers = new ArrayList<>();
        // the hierarchy iterates from the root to the most specific element
        hierarchy.forEach(layers::add);
        Collections.reverse(layers);
        return layers;
    }

    /**
     * Builds the annotation from the members resolved against the environment, so that the members it
     * returns are the ones its equality and hash code are computed from. The members not set on the
     * annotation default to the defaults of the annotation type, which can hold placeholders too.
     */
    private <T extends Annotation> ResolvedAnnotation<T> buildResolvedAnnotation(Environment environment, Class<T> annotationClass, AnnotationValue<T> value) {
        // the mapper of the environment annotation value also expands comma separated array values
        UnaryOperator<Object> mapper = Optional.ofNullable(new EnvironmentAnnotationValue<>(environment, value).getValueMapper())
            .<UnaryOperator<Object>>map(valueMapper -> valueMapper::apply)
            .orElse(UnaryOperator.identity());
        Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>();
        boolean environmentDependent = false;
        for (Map.Entry<CharSequence, Object> entry : value.getValues().entrySet()) {
            Object resolvedValue = resolve(mapper, entry.getValue());
            if (resolvedValue == UNRESOLVED) {
                // resolved when the member is read, once the environment has the property
                environmentDependent = true;
                resolvedValue = entry.getValue();
            } else {
                // the nested annotations are resolved when their members are read
                environmentDependent |= !Objects.deepEquals(resolvedValue, entry.getValue())
                    || resolvedValue instanceof AnnotationValue<?>
                    || resolvedValue instanceof AnnotationValue<?>[];
            }
            resolvedValues.put(entry.getKey(), resolvedValue);
        }
        for (Map.Entry<CharSequence, Object> entry : AnnotationDefaults.of(annotationClass).entrySet()) {
            if (!resolvedValues.containsKey(entry.getKey())) {
                Object resolvedDefault = resolve(mapper, entry.getValue());
                if (resolvedDefault == UNRESOLVED) {
                    // resolved when the member is read, once the environment has the property
                    environmentDependent = true;
                    resolvedValues.put(entry.getKey(), entry.getValue());
                } else if (!Objects.deepEquals(resolvedDefault, entry.getValue())) {
                    environmentDependent = true;
                    resolvedValues.put(entry.getKey(), resolvedDefault);
                }
            }
        }
        AnnotationValue<T> resolvedValue = new AnnotationValue<>(
            value.getAnnotationName(),
            resolvedValues,
            value.getDefaultValues(),
            value.getRetentionPolicy(),
            value.getStereotypes()
        );
        return new ResolvedAnnotation<>(
            AnnotationMetadataSupport.buildAnnotation(annotationClass, new EnvironmentAnnotationValue<>(environment, resolvedValue)),
            environmentDependent
        );
    }

    /**
     * Resolves the value, or keeps it when a placeholder cannot be resolved: the environment annotation value then
     * fails only when the member is read, like it does for the members that are not synthesized.
     */
    private static Object resolveOrKeep(UnaryOperator<Object> mapper, Object value) {
        Object resolved = resolve(mapper, value);
        return resolved == UNRESOLVED ? value : resolved;
    }

    /**
     * Resolves the value.
     *
     * @return The resolved value, or {@link #UNRESOLVED} when a placeholder cannot be resolved
     */
    private static Object resolve(UnaryOperator<Object> mapper, Object value) {
        try {
            return mapper.apply(value);
        } catch (ConfigurationException e) {
            return UNRESOLVED;
        }
    }

    /**
     * A synthesized annotation.
     *
     * @param annotation The annotation
     * @param environmentDependent Whether a member was resolved from the environment
     * @param <T> The annotation type
     */
    private record ResolvedAnnotation<T extends Annotation>(T annotation, boolean environmentDependent) {
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

    /**
     * The annotations synthesized for an environment that do not depend on it.
     */
    private static final class SynthesizedAnnotations {
        private final Environment environment;
        private final Map<String, Annotation> allByName = new ConcurrentHashMap<>();
        private final Map<String, Annotation> declaredByName = new ConcurrentHashMap<>();
        private final AtomicReference<Annotation @Nullable []> all = new AtomicReference<>();
        private final AtomicReference<Annotation @Nullable []> declared = new AtomicReference<>();

        private SynthesizedAnnotations(Environment environment) {
            this.environment = environment;
        }
    }

    /**
     * The lazily computed declared metadata and synthesized annotations.
     *
     * @param declaredTargetMetadata The declared metadata of the target metadata
     * @param declaredMetadata The environment aware declared metadata
     * @param synthesizedAnnotations The synthesized annotations
     */
    private record LazyState(@Nullable AnnotationMetadata declaredTargetMetadata,
                             @Nullable AnnotationMetadata declaredMetadata,
                             @Nullable SynthesizedAnnotations synthesizedAnnotations) {

        private static final LazyState EMPTY = new LazyState(null, null, null);

        LazyState withDeclaredTargetMetadata(AnnotationMetadata metadata) {
            return new LazyState(metadata, declaredMetadata, synthesizedAnnotations);
        }

        LazyState withDeclaredMetadata(AnnotationMetadata metadata) {
            return new LazyState(declaredTargetMetadata, metadata, synthesizedAnnotations);
        }

        LazyState withSynthesizedAnnotations(SynthesizedAnnotations synthesized) {
            return new LazyState(declaredTargetMetadata, declaredMetadata, synthesized);
        }
    }

    /**
     * The declared metadata of an environment aware metadata, resolved against the same environment.
     */
    private static final class DeclaredEnvironmentAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {

        private final AbstractEnvironmentAnnotationMetadata parent;

        DeclaredEnvironmentAnnotationMetadata(AnnotationMetadata declaredMetadata, AbstractEnvironmentAnnotationMetadata parent) {
            super(declaredMetadata);
            this.parent = parent;
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return parent.getEnvironment();
        }
    }
}
