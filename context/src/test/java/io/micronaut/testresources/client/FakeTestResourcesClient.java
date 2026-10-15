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
package io.micronaut.testresources.client;

import io.micronaut.context.env.ActiveEnvironment;
import io.micronaut.context.env.PropertyExpressionResolver;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.value.PropertyResolver;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stands for the Micronaut Test Resources client in the tests of the training run, which has no
 * dependency on Test Resources. It sits in the package of the client and works as the client does
 * with a server that can supply {@value #PROPERTY}:
 * <ul>
 *     <li>{@link Loader} adds a property source that, when the environment reads it, reads the switch
 *     of the client, the system property {@code micronaut.test.resources.enabled}, as
 *     {@code TestResourcesClientFactory} does. Unless it is {@code false}, it supplies
 *     {@value #PROPERTY}, which the configuration lacks, as a placeholder.</li>
 *     <li>{@link Resolver} resolves that placeholder when something reads the property. This is where
 *     the Test Resources server starts a container.</li>
 * </ul>
 * The tests register both classes with a class loader of their own, so the other tests do not see them.
 */
public final class FakeTestResourcesClient {
    /**
     * The property that the configuration lacks and that the fake supplies.
     */
    public static final String PROPERTY = "datasources.default.url";
    /**
     * The value that the fake resolves the property to.
     */
    public static final String VALUE = "jdbc:postgresql://container-started-by-test-resources:5432/test";
    /**
     * What the fake prints when it computes its properties, followed by the switch and the properties.
     */
    public static final String COMPUTED = "Fake Test Resources client: micronaut.test.resources.enabled=";
    /**
     * What the fake prints when it resolves a property, followed by the expression.
     */
    public static final String RESOLVED_MESSAGE = "Fake Test Resources client: resolved ";
    /**
     * The value of the switch of the client when the property source computed its properties, or
     * {@code null} before.
     */
    public static final AtomicReference<String> SWITCH = new AtomicReference<>();
    /**
     * How many times the resolver resolved the property: the containers a server would start.
     */
    public static final AtomicInteger RESOLVED = new AtomicInteger();

    private static final String CLIENT_ENABLED = "micronaut.test.resources.enabled";
    private static final String PLACEHOLDER_PREFIX = "auto.test.resources.";

    private FakeTestResourcesClient() {
    }

    /**
     * Forgets what the fake has seen.
     */
    public static void reset() {
        SWITCH.set(null);
        RESOLVED.set(0);
    }

    /**
     * The property source loader of the fake client.
     */
    public static final class Loader implements PropertySourceLoader {
        @Override
        public Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
            return Optional.of(new LazySource(resourceLoader, true));
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

    /**
     * The expression resolver of the fake client.
     */
    public static final class Resolver implements PropertyExpressionResolver {
        @Override
        public <T> Optional<T> resolve(PropertyResolver propertyResolver, ConversionService conversionService, String expression, Class<T> requiredType) {
            if (!expression.startsWith(PLACEHOLDER_PREFIX)) {
                return Optional.empty();
            }
            // A Test Resources server starts the container here
            RESOLVED.incrementAndGet();
            System.out.println(RESOLVED_MESSAGE + expression);
            return conversionService.convert(VALUE, requiredType);
        }
    }

    /**
     * A property source that computes its properties when the environment reads it, as the one of
     * Test Resources does: only the properties that the environment does not have yet.
     */
    public static final class LazySource implements PropertySource {
        private final ResourceLoader resourceLoader;
        private final boolean readsSwitch;
        private List<String> keys;

        /**
         * @param resourceLoader The environment that reads the property source
         * @param readsSwitch Whether the property source supplies nothing when the switch of the client is {@code false}
         */
        public LazySource(ResourceLoader resourceLoader, boolean readsSwitch) {
            this.resourceLoader = resourceLoader;
            this.readsSwitch = readsSwitch;
        }

        @Override
        public String getName() {
            return "test resources";
        }

        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE;
        }

        @Override
        public Object get(String key) {
            return "${" + PLACEHOLDER_PREFIX + key + "}";
        }

        @Override
        public synchronized Iterator<String> iterator() {
            if (keys == null) {
                String enabled = System.getProperty(CLIENT_ENABLED, "true");
                SWITCH.set(enabled);
                boolean missing = !(resourceLoader instanceof PropertyResolver propertyResolver) || !propertyResolver.containsProperties(PROPERTY);
                keys = (!readsSwitch || Boolean.parseBoolean(enabled)) && missing ? List.of(PROPERTY) : List.of();
                System.out.println(COMPUTED + enabled + ", supplies " + keys);
            }
            return keys.iterator();
        }
    }
}
