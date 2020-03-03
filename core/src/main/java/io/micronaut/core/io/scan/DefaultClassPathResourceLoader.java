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
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads resources from the classpath.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public class DefaultClassPathResourceLoader implements ClassPathResourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultClassPathResourceLoader.class);

    private final ClassLoader classLoader;
    private final String basePath;
    private final Map<String, Boolean> isDirectoryCache = new ConcurrentLinkedHashMap.Builder<String, Boolean>()
            .maximumWeightedCapacity(50).build();

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
        if (!isDirectory(path)) {
            return Optional.ofNullable(classLoader.getResourceAsStream(prefixPath(path)));
        }
        return Optional.empty();
    }

    /**
     * Obtains a resource URL.
     *
     * @param path The path
     * @return An optional resource
     */
    public Optional<URL> getResource(String path) {
        boolean isDirectory = isDirectory(path);

        if (!isDirectory) {
            URL url = classLoader.getResource(prefixPath(path));
            return Optional.ofNullable(url);
        }
        return Optional.empty();
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
        Stream.Builder<URL> builder = Stream.builder();
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

    @SuppressWarnings("ConstantConditions")
    private boolean isDirectory(String path) {
        return isDirectoryCache.computeIfAbsent(path, s -> {
            URL url = classLoader.getResource(prefixPath(path));
            if (url != null) {
                try {
                    URI uri = url.toURI();
                    Path pathObject;
                    synchronized (DefaultClassPathResourceLoader.class) {

                        if (uri.getScheme().equals("jar")) {
                            FileSystem fileSystem = null;
                            try {
                                try {
                                    fileSystem = FileSystems.getFileSystem(uri);
                                } catch (FileSystemNotFoundException e) {
                                    //no-op
                                }
                                if (fileSystem == null || !fileSystem.isOpen()) {
                                    fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap(), classLoader);
                                }

                                pathObject = fileSystem.getPath(path);
                                return pathObject == null || Files.isDirectory(pathObject);
                            } finally {
                                if (fileSystem != null && fileSystem.isOpen()) {
                                    try {
                                        fileSystem.close();
                                    } catch (IOException e) {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("Error shutting down JAR file system [" + fileSystem + "]: " + e.getMessage(), e);
                                        }
                                    }
                                }
                            }
                        } else if (uri.getScheme().equals("file")) {
                            pathObject = Paths.get(uri);
                            return pathObject == null || Files.isDirectory(pathObject);
                        }

                    }
                } catch (URISyntaxException | IOException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error establishing whether path is a directory: " + e.getMessage(), e);
                    }
                }
            }
            return path.indexOf('.') == -1; // fallback to less sophisticated approach
        });
    }

    @SuppressWarnings("MagicNumber")
    private String prefixPath(String path) {
        if (path.startsWith("classpath:")) {
            path = path.substring(10);
        }
        if (basePath != null) {
            if (path.startsWith("/")) {
                return basePath + path.substring(1);
            }
            return basePath + path;
        }
        return path;
    }

}
