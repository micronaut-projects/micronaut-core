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
import org.particleframework.core.value.OptionalValuesMap;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extended version of {@link OptionalValuesMap} that resolved place holders
 * @author graemerocher
 * @since 1.0
 */
class EnvironmentOptionalValuesMap<V> extends OptionalValuesMap<V> {
    EnvironmentOptionalValuesMap(Class<?> type, Map<CharSequence, ?> values, Environment environment) {
        super(type, resolveValues(environment, values));
    }

    @SuppressWarnings("unchecked")
    private static Map<CharSequence, ?> resolveValues(Environment environment, Map<CharSequence, ?> values) {
        PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
        return values.entrySet().stream().map((Function<Map.Entry<CharSequence, ?>, Map.Entry<CharSequence, ?>>) entry -> {
            Object value = entry.getValue();
            if(value instanceof CharSequence) {
                value = placeholderResolver.resolveRequiredPlaceholder(value.toString());
                ((Map.Entry)entry).setValue(value);
            }
            else if(value instanceof String[]) {
                String[] a = (String[]) value;
                for (int i = 0; i < a.length; i++) {
                    a[i] = placeholderResolver.resolveRequiredPlaceholder(a[i]);
                }
                ((Map.Entry)entry).setValue(a);
            }
            return entry;
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
