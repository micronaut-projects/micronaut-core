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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.type.Argument;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Extended version of {@link ConvertibleValuesMap} that resolves placeholders based on the environment.
 *
 * @param <V> generic value
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class EnvironmentConvertibleValuesMap<V> extends ConvertibleValuesMap<V> {

    private final Environment environment;
    private final ConversionService conversionService;

    /**
     * @param map         A map of values
     * @param environment The environment
     */
    EnvironmentConvertibleValuesMap(Map<? extends CharSequence, V> map, Environment environment) {
        super(map, environment.getConversionService());
        this.environment = environment;
        this.conversionService = environment.getConversionService();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return get(name, ConversionContext.of(requiredType));
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        return get(name, ConversionContext.of(requiredType));
    }

    @Override
    public <T> T get(CharSequence name, Class<T> requiredType, T defaultValue) {
        return get(name, ConversionContext.of(requiredType)).orElse(defaultValue);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        V value = map.get(name);
        if (value instanceof AnnotationClassValue<?> acv) {
            return conversionService.convert(acv, conversionContext);
        } else if (value instanceof CharSequence sequence) {
            PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
            String str = doResolveIfNecessary(sequence, placeholderResolver);
            return conversionService.convert(str, conversionContext);
        } else if (value instanceof String[] values) {
            PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
            String[] resolved = Arrays.stream(values)
                .flatMap(val -> placeholderResolver.resolveOptionalPlaceholder(val, String[].class)
                    .map(Arrays::stream)
                    .orElseGet(() -> Stream.of(doResolveIfNecessary(val, placeholderResolver))))
                .toArray(String[]::new);
            return conversionService.convert(resolved, conversionContext);
        } else if (value instanceof AnnotationValue<?>[] annotationValues) {
            io.micronaut.core.annotation.AnnotationValue<?>[] b = new AnnotationValue[annotationValues.length];
            for (int i = 0; i < annotationValues.length; i++) {
                io.micronaut.core.annotation.AnnotationValue<?> annotationValue = annotationValues[i];
                b[i] = new EnvironmentAnnotationValue<>(environment, annotationValue);
            }
            return conversionService.convert(b, conversionContext);
        } else if (value instanceof AnnotationValue<?> av) {
            av = new EnvironmentAnnotationValue<>(environment, av);
            return conversionService.convert(av, conversionContext);
        } else {
            return super.get(name, conversionContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<V> values() {
        return super.values().stream().map(v -> {
            if (v instanceof CharSequence) {
                v = (V) environment.getPlaceholderResolver().resolveRequiredPlaceholders(v.toString());
            }
            return v;
        }).toList();
    }

    private String doResolveIfNecessary(CharSequence value, PropertyPlaceholderResolver placeholderResolver) {
        String str = value.toString();
        if (str.contains(placeholderResolver.getPrefix())) {
            str = placeholderResolver.resolveRequiredPlaceholders(str);
        }
        return str;
    }

    /**
     * Creates a new {@link ConvertibleValues} for the values.
     *
     * @param environment The environment
     * @param values      A map of values
     * @param <T>         The target generic type
     * @return The values
     */
    static <T> ConvertibleValues<T> of(Environment environment, Map<? extends CharSequence, T> values) {
        if (values == null) {
            return ConvertibleValuesMap.empty();
        } else {
            return new EnvironmentConvertibleValuesMap<>(values, environment);
        }
    }
}
