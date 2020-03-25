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
package io.micronaut.core.io.scan;

import io.micronaut.core.io.ResourceLoader;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Abstraction to load resources from the the classpath.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ClassPathResourceLoader extends ResourceLoader {

    /**
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
}
