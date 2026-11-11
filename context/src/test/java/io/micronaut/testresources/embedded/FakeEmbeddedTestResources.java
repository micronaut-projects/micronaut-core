/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.testresources.embedded;

import io.micronaut.context.env.ActiveEnvironment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.testresources.client.FakeTestResourcesClient;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Stands for a module of Micronaut Test Resources other than the client, such as
 * {@code micronaut-test-resources-embedded}, which starts the containers in the application's own JVM:
 * it supplies its properties whatever the switch of the client says.
 */
public final class FakeEmbeddedTestResources {

    private FakeEmbeddedTestResources() {
    }

    /**
     * The property source loader of the fake module.
     */
    public static final class Loader implements PropertySourceLoader {
        @Override
        public Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
            return Optional.of(new FakeTestResourcesClient.LazySource(resourceLoader, false));
        }

        @Override
        public Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
            return load(resourceName, resourceLoader);
        }

        @Override
        public Map<String, Object> read(String name, InputStream input) {
            return Map.of();
        }
    }
}
