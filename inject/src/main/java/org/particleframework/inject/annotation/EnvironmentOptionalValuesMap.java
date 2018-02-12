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
import org.particleframework.core.value.OptionalValuesMap;

import java.util.Map;
import java.util.Optional;

/**
 * Extended version of {@link OptionalValuesMap} that resolved place holders
 * @author graemerocher
 * @since 1.0
 */
class EnvironmentOptionalValuesMap<V> extends OptionalValuesMap<V> {
    private final Environment environment;

    EnvironmentOptionalValuesMap(Class<?> type, Map<CharSequence, ?> values, Environment environment) {
        super(type, values);
        this.environment = environment;
    }

    @Override
    public Optional<V> get(CharSequence name) {
        if(name != null) {
            name = environment.getPlaceholderResolver().resolveRequiredPlaceholder(name.toString());
        }
        return super.get(name);
    }
}
