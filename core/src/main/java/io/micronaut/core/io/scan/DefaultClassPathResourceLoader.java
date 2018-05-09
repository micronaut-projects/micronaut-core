/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads resources from the classpath.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DefaultClassPathResourceLoader implements ClassPathResourceLoader {

    private final ClassLoader classLoader;
    private final String basePath;

    /**
     * Default constructor.
     *
     * @param classLoader The class loader for loading resources
     */
    public DefaultClassPathResourceLoader(ClassLoader classLoader) {
        this(classLoader, null);
    }

    /**
     * Use when resources should have a standard base path.
     *
     * @param classLoader The class loader for loading resources
     * @param basePath    The path to look for resources under
     */
    public DefaultClassPathResourceLoader(ClassLoader classLoader, String basePath) {
        this.classLoader = classLoader;
        this.basePath = normalize(basePath);
    }

    /**
     * Obtains a resource as a stream.
     *
     * @param path The path
     * @return An optional resource
     */
    public Optional<InputStream> getResourceAsStream(String path) {
        return Optional.ofNullable(classLoader.getResourceAsStream(prefixPath(path)));
    }

    /**
     * Obtains a resource URL.
     *
     * @param path The path
     * @return An optional resource
     */
    public Optional<URL> getResource(String path) {
        return Optional.ofNullable(classLoader.getResource(prefixPath(path)));
    }

    /**
     * Obtains a stream of resource URLs.
     *
     * @param path The path
     * @return A resource stream
     */
    public Stream<URL> getResources(String path) {
        Enumeration<URL> all;
        try {
            all = classLoader.getResources(prefixPath(path));
        } catch (IOException e) {
            return Stream.empty();
        }
        Stream.Builder<URL> builder = Stream.<URL>builder();
        while (all.hasMoreElements()) {
            URL url = all.nextElement();
            builder.accept(url);
        }
        return builder.build();
    }

    /**
     * @return The class loader used to retrieve resources
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @param basePath The path to load resources
     * @return The resouce loader
     */
    public ResourceLoader forBase(String basePath) {
        return new DefaultClassPathResourceLoader(classLoader, basePath);
    }

    @SuppressWarnings("MagicNumber")
    private String normalize(String path) {
        if (path != null) {
            if (path.startsWith("classpath:")) {
                path = path.substring(10);
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!path.endsWith("/") && StringUtils.isNotEmpty(path)) {
                path = path + "/";
            }
        }
        return path;
    }

    @SuppressWarnings("MagicNumber")
    private String prefixPath(String path) {
        if (path.startsWith("classpath:")) {
            path = path.substring(10);
        }
        if (basePath != null) {
            if (path.startsWith("/")) {
                return basePath + path.substring(1);
            } else {
                return basePath + path;
            }
        } else {
            return path;
        }
    }
}
