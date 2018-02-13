/*
 * Copyright 2018 original authors
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
package org.particleframework.inject.annotation;

import org.particleframework.context.env.Environment;
import org.particleframework.context.env.PropertyPlaceholderResolver;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.convert.value.ConvertibleValuesMap;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extended version of {@link ConvertibleValuesMap} that resolves placeholders based on the environment
 *
 * @author graemerocher
 * @since 1.0
 */
class EnvironmentConvertibleValuesMap<V> extends ConvertibleValuesMap<V> {

    private final Environment environment;

    EnvironmentConvertibleValuesMap(Map<? extends CharSequence, V> map, Environment environment) {
        super(map, environment);
        this.environment = environment;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        V value = map.get(name);
        PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
        if(value instanceof CharSequence) {
            String resolved = placeholderResolver.resolveRequiredPlaceholder(value.toString());
            return environment.convert(resolved, conversionContext);
        }
        else if(value instanceof String[]) {
            String[] a = (String[]) value;
            for (int i = 0; i < a.length; i++) {
                a[i] = placeholderResolver.resolveRequiredPlaceholder(a[i]);
            }
            return environment.convert(a, conversionContext);
        }
        else {
            return super.get(name, conversionContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<V> values() {
        return super.values().stream().map(v -> {
            if(v instanceof CharSequence) {
                v = (V) environment.getPlaceholderResolver().resolveRequiredPlaceholder(v.toString());
            }
            return v;
        }).collect(Collectors.toList());
    }

    /**
     * Creates a new {@link ConvertibleValues} for the values
     *
     * @param values A map of values
     * @param <T> The target generic type
     * @return The values
     */
    static <T> ConvertibleValues<T> of(Environment environment, Map<? extends CharSequence, T> values ) {
        if(values == null) {
            return ConvertibleValuesMap.empty();
        }
        else {
            return new EnvironmentConvertibleValuesMap<>(values, environment);
        }
    }
}
