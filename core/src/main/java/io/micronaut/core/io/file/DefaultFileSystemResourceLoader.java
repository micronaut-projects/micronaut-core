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
package io.micronaut.core.io.file;

import io.micronaut.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;


/**
 * Loads resources from the file system.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DefaultFileSystemResourceLoader implements FileSystemResourceLoader {

    private final Path baseDirPath;
    private final boolean baseExists;

    /**
     * Default constructor.
     */
    public DefaultFileSystemResourceLoader() {
        this.baseDirPath = null;
        this.baseExists = true;
    }

    /**
     * @param baseDirPath The base directory
     */
    public DefaultFileSystemResourceLoader(File baseDirPath) {
        this(baseDirPath.toPath().normalize());
    }

    /**
     * @param path The path
     */
    public DefaultFileSystemResourceLoader(String path) {
        this(Paths.get(normalize(path)));
    }

    /**
     * @param path The path
     */
    public DefaultFileSystemResourceLoader(Path path) {
        Path baseDirPath;
        try {
            baseDirPath = path.normalize().toRealPath();
        } catch (IOException e) {
            baseDirPath = null;
        }
        this.baseExists = baseDirPath != null;
        this.baseDirPath = baseDirPath;
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        Path filePath = getFilePath(normalize(path));
        if (isResolvableFile(filePath)) {
            try {
                return Optional.of(Files.newInputStream(filePath));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<URL> getResource(String path) {
        Path filePath = getFilePath(normalize(path));
        if (isResolvableFile(filePath)) {
            try {
                URL url = filePath.toUri().toURL();
                return Optional.of(url);
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        return Optional.empty();
    }

    @Override
    public Stream<URL> getResources(String name) {
        return getResource(name).map(Stream::of).orElseGet(Stream::empty);
    }

    /**
     * @param basePath The path to load resources
     * @return The resource loader
     */
    @Override
    public ResourceLoader forBase(String basePath) {
        return new DefaultFileSystemResourceLoader(basePath);
    }

    private boolean isResolvableFile(Path filePath) {
        return startsWithBase(filePath) && Files.exists(filePath) && Files.isReadable(filePath) && !Files.isDirectory(filePath);
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

    private Path getFilePath(String path) {
        if (baseDirPath != null) {
            return baseDirPath.resolve(path);
        } else {
            return Paths.get(path);
        }
    }

    private boolean startsWithBase(Path path) {
        if (baseDirPath != null) {
            Path relativePath;
            try {
                relativePath = baseDirPath.resolve(path).toRealPath();
                return relativePath.startsWith(baseDirPath);
            } catch (IOException e) {
                return false;
            }
        }
        return baseExists;
    }
}
