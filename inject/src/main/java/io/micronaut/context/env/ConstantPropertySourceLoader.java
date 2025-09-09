/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.optim.StaticOptimizations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of the {@link PropertySourceLoader} interface that provides constant property sources
 * for use in a configuration context.
 *
 * This class retrieves pre-defined property sources from {@link StaticOptimizations} and allows loading of these
 * property sources either globally or specifically for an active environment.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class ConstantPropertySourceLoader implements PropertySourceLoader {

    private final List<PropertySource> constantPropertySources;

    public ConstantPropertySourceLoader() {
        constantPropertySources = StaticOptimizations.get(ConstantPropertySources.class)
        .map(ConstantPropertySources::getSources)
        .orElse(Collections.emptyList());
    }

    @Override
    public boolean isEnabled() {
        return !constantPropertySources.isEmpty();
    }

    @Override
    public Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
        for (PropertySource p : constantPropertySources) {
            if (p.getName().equals(resourceName)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    };

    @Override
    public Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
        for (PropertySource p : constantPropertySources) {
            if (p.getName().equals(resourceName + "-" + activeEnvironment.getName())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> read(String name, InputStream input) throws IOException {
        return Map.of();
    }
}
