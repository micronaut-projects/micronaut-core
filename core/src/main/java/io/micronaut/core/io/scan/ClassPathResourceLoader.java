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
package io.micronaut.core.io.scan;

import io.micronaut.core.io.ResourceConflictException;
import io.micronaut.core.io.ResourceDuplicateException;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;
import io.micronaut.core.io.ResourceLoader;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstraction to load resources from the classpath.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@NullMarked
public interface ClassPathResourceLoader extends ResourceLoader {

    /**
     * Hook to handle duplicate resources for {@link ResourceLoadStrategyType#FIRST_MATCH}.
     * Default implementation is a no-op.
     *
     * @param resourceName The resource name
     * @param chosen       The chosen resource URL
     * @param duplicates   The duplicate resource URLs
     * @since 5.0.0
     */
    default void handleResourceDuplicates(String resourceName, URL chosen, List<URL> duplicates) {
    }

    /**
     * Returns the underlying classloader used by this {@link ClassPathResourceLoader}.
     *
     * @return The underlying classloader used by this {@link ClassPathResourceLoader}
     */
    ClassLoader getClassLoader();

    /**
     * @param path The path to a resource including a prefix
     *             appended by a colon. Ex (classpath:, file:)
     * @return Whether the given resource loader supports the prefix
     */
    @Override
    default boolean supportsPrefix(String path) {
        return path.startsWith("classpath:");
    }

    /**
     * Return the default {@link ClassPathResourceLoader} for the given class loader.
     *
     * @param classLoader The classloader
     * @return The default loader
     */
    static ClassPathResourceLoader defaultLoader(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = ClassPathResourceLoader.class.getClassLoader();
        }
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return new DefaultClassPathResourceLoader(classLoader);
    }

    /**
     * List resources for the given name, handling duplicate URLs and implementations that may return {@code null}.
     *
     * @param resourceLoader The resource loader
     * @param name The resource name
     * @return An immutable list of unique URLs in encounter order
     * @since 5.0.0
     */
    static List<URL> listUniqueResources(ResourceLoader resourceLoader, String name) {
        Stream<URL> stream = Optional.ofNullable(resourceLoader.getResources(name)).orElseGet(Stream::empty);
        try (stream) {
            LinkedHashMap<String, URL> unique = new LinkedHashMap<>();
            stream.forEach(url -> unique.putIfAbsent(url.toExternalForm(), url));
            return List.copyOf(unique.values());
        }
    }

    /**
     * Resolve resources for the given name, applying the configured strategy.
     *
     * @param resourceLoader The resource loader
     * @param name           The resource name
     * @param strategy       The strategy
     * @return An immutable list of unique URLs in encounter order
     * @throws ResourceDuplicateException If multiple resources are found and the configured strategy is
     *                                   {@link ResourceLoadStrategyType#FAIL_ON_DUPLICATE}
     * @throws ResourceConflictException  If multiple resources are found and the configured strategy is
     *                                   {@link ResourceLoadStrategyType#MERGE_ALL}
     * @since 5.0.0
     */
    static List<URL> resolveResources(ResourceLoader resourceLoader, String name, ResourceLoadStrategy strategy) {
        List<URL> urls = listUniqueResources(resourceLoader, name);
        if (urls.size() <= 1) {
            return urls;
        }

        ResourceLoadStrategyType type = strategy.type();
        if (type == ResourceLoadStrategyType.FAIL_ON_DUPLICATE) {
            throw new ResourceDuplicateException(name, urls);
        }
        if (type == ResourceLoadStrategyType.MERGE_ALL) {
            throw new ResourceConflictException(name, urls);
        }

        if (strategy.warnOnDuplicates() && resourceLoader instanceof ClassPathResourceLoader) {
            URL chosen = urls.getFirst();
            List<URL> duplicates = urls.subList(1, urls.size());
            ((ClassPathResourceLoader) resourceLoader).handleResourceDuplicates(name, chosen, duplicates);
        }
        return urls;
    }
}
