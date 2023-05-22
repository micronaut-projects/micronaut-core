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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adapts an {@link AnnotationValue} to the environment.
 *
 * @author graemerocher
 * @since 1.0
 * @param <A> The annotation type
 */
@Internal
class EnvironmentAnnotationValue<A extends Annotation> extends AnnotationValue<A> {

    private final Environment environment;

    /**
     * Default constructor.
     *
     * @param environment The environment
     * @param target The target
     */
    EnvironmentAnnotationValue(Environment environment, AnnotationValue<A> target) {
        super(target, AnnotationMetadataSupport.getDefaultValues(target.getAnnotationName()), EnvironmentConvertibleValuesMap.of(
                environment,
                target.getValues()
        ), environment != null ? o -> {
            PropertyPlaceholderResolver resolver = environment.getPlaceholderResolver();
            if (o instanceof String) {
                String v = (String) o;
                if (v.contains(resolver.getPrefix())) {
                    return resolver.resolveRequiredPlaceholders(v);
                }
            } else if (o instanceof String[]) {
                String[] values = (String[]) o;
                String[] resolvedValues = Arrays.copyOf(values, values.length);
                boolean expandValues = false;
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (value.contains(resolver.getPrefix())) {
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
            }
            return o;
        } : null);
        this.environment = environment;
    }

    @Override
    public @NonNull
    <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member, Class<T> type) {
        List<AnnotationValue<T>> annotationValues = super.getAnnotations(member, type);
        return annotationValues.stream().map(av -> new EnvironmentAnnotationValue<>(environment, av)).collect(Collectors.toList());
    }

    @Override
    public @NonNull
    <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member) {
        List<AnnotationValue<T>> annotationValues = super.getAnnotations(member);
        return annotationValues.stream().map(av -> new EnvironmentAnnotationValue<>(environment, av)).collect(Collectors.toList());
    }

    @Override
    public @NonNull
    <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(String member, Class<T> type) {
        Optional<AnnotationValue<T>> annotationValue = super.getAnnotation(member, type);
        return annotationValue.map(av -> new EnvironmentAnnotationValue<>(environment, av));
    }

    @Override
    public @NonNull
    <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(@NonNull String member) {
        Optional<AnnotationValue<T>> annotationValue = super.getAnnotation(member);
        return annotationValue.map(av -> new EnvironmentAnnotationValue<>(environment, av));
    }
}
