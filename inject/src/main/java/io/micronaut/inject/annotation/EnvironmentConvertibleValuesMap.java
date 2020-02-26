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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.type.Argument;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extended version of {@link ConvertibleValuesMap} that resolves placeholders based on the environment.
 *
 * @param <V> generic valu
 * @author graemerocher
 * @since 1.0
 */
@Internal
class EnvironmentConvertibleValuesMap<V> extends ConvertibleValuesMap<V> {

    private final Environment environment;

    /**
     * @param map         A map of values
     * @param environment The environment
     */
    EnvironmentConvertibleValuesMap(Map<? extends CharSequence, V> map, Environment environment) {
        super(map, environment);
        this.environment = environment;
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
        if (value instanceof AnnotationClassValue) {
            AnnotationClassValue acv = (AnnotationClassValue) value;
            return environment.convert(acv, conversionContext);
        } else if (value instanceof CharSequence) {
            PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
            String str = doResolveIfNecessary((CharSequence) value, placeholderResolver);
            return environment.convert(str, conversionContext);
        } else if (value instanceof String[]) {
            PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
            String[] resolved = Arrays.stream((String[]) value)
                .flatMap(val -> {
                    try {
                        String[] values = placeholderResolver.resolveRequiredPlaceholder(val, String[].class);
                        return Arrays.stream(values);
                    } catch (ConfigurationException e) {
                        return Stream.of(doResolveIfNecessary(val, placeholderResolver));
                    }
                })
                .toArray(String[]::new);
            return environment.convert(resolved, conversionContext);
        } else if (value instanceof io.micronaut.core.annotation.AnnotationValue[]) {
            io.micronaut.core.annotation.AnnotationValue[] annotationValues = (io.micronaut.core.annotation.AnnotationValue[]) value;
            io.micronaut.core.annotation.AnnotationValue[] b = new AnnotationValue[annotationValues.length];
            for (int i = 0; i < annotationValues.length; i++) {
                io.micronaut.core.annotation.AnnotationValue annotationValue = annotationValues[i];
                b[i] = new EnvironmentAnnotationValue(environment, annotationValue);
            }
            return environment.convert(b, conversionContext);
        } else if (value instanceof io.micronaut.core.annotation.AnnotationValue) {
            io.micronaut.core.annotation.AnnotationValue av = (io.micronaut.core.annotation.AnnotationValue) value;
            av = new EnvironmentAnnotationValue(environment, av);
            return environment.convert(av, conversionContext);
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
        }).collect(Collectors.toList());
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
