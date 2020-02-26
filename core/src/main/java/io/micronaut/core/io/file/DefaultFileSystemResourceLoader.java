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
package io.micronaut.core.io.file;

import io.micronaut.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads resources from the file system.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DefaultFileSystemResourceLoader implements FileSystemResourceLoader {

    private final Optional<File> baseDir;

    /**
     * Default constructor.
     */
    public DefaultFileSystemResourceLoader() {
        this.baseDir = Optional.empty();
    }

    /**
     * @param baseDir The base directory
     */
    public DefaultFileSystemResourceLoader(File baseDir) {
        this.baseDir = Optional.of(baseDir);
    }

    /**
     * @param path The path
     */
    public DefaultFileSystemResourceLoader(String path) {
        this.baseDir = Optional.of(new File(normalize(path)));
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        File file = getFile(normalize(path));
        try {
            return Optional.of(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<URL> getResource(String path) {
        File file = getFile(normalize(path));
        if (file.exists() && file.canRead() && !file.isDirectory()) {
            try {
                URL url = file.toURI().toURL();
                return Optional.of(url);
            } catch (MalformedURLException e) {
            }
        }
        return Optional.empty();
    }

    @Override
    public Stream<URL> getResources(String name) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support retrieving a stream of resources");
    }

    /**
     * @param basePath The path to load resources
     * @return The resource loader
     */
    public ResourceLoader forBase(String basePath) {
        return new DefaultFileSystemResourceLoader(basePath);
    }

    @SuppressWarnings("MagicNumber")
    private static String normalize(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
        }
        return path;
    }

    private File getFile(String path) {
        return baseDir.map(dir -> new File(dir, path)).orElse(new File(path));
    }
}
